package com.fayu.message.server.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.dto.SaveOutboxDTO;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ConversationVO;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.message.config.MessageRetryProperties;
import com.fayu.message.domain.dto.MessagePayloadDTO;
import com.fayu.message.domain.po.Message;
import com.fayu.message.enums.EventType;
import com.fayu.message.enums.OutboxStatusEnum;
import com.fayu.message.enums.YesNoStatus;
import com.fayu.message.mapper.MessageMapper;
import com.fayu.message.mapper.MessageOutboxMapper;
import com.fayu.message.server.IConversationServer;
import com.fayu.message.server.IMessageOutboxServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import static com.fayu.common.constant.MessageEventKey.MESSAGE_CREATE_EVENT_KEY;
import static com.fayu.common.constant.RedisConstant.MESSAGE_ID;
import static com.fayu.common.constant.RedisConstant.SEQ_INC;
import static com.fayu.common.constant.RedisConstant.SINGLE_CONVERSATION_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageOutboxServerImpl extends ServiceImpl<MessageOutboxMapper, MessageOutbox> implements IMessageOutboxServer {

    private final MessageRetryProperties messageRetryProperties;
    private final MessageMapper messageMapper;
    private final IConversationServer conversationServer;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public SaveMsgVO saveOutbox(SaveOutboxDTO saveOutboxDTO) {
        Long toId = saveOutboxDTO.getToId();
        Long senderId = saveOutboxDTO.getSenderId();
        String clientMessageId = saveOutboxDTO.getClientMessageId();
        boolean timing = sampleTiming(clientMessageId);

        long queryStartedAt = System.nanoTime();
        String eventKey = MESSAGE_CREATE_EVENT_KEY.formatted(senderId, clientMessageId);
        MessageOutbox existing = lambdaQuery()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, EventType.MESSAGE_CREATE.toString())
                .one();

        if (timing) {
            log.info("[PERF_QUERY] clientMessageId={} totalMs={}",
                    clientMessageId, elapsedMs(queryStartedAt));
        }

        long timeAt;
        if (existing != null) {
            timeAt = existing.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            log.debug("重复消息 messageId = {}", existing.getMessageId());
            return SaveMsgVO.builder()
                    .reuseMessage(true)
                    .messageId(existing.getMessageId())
                    .seq(existing.getSeq())
                    .conversationId(existing.getConversationId())
                    .timeAt(timeAt)
                    .build();
        }

        long routeStartedAt = System.nanoTime();

        ConversationVO conversation;
        Long conversationId;
        Long lowId = Math.min(toId, senderId);
        Long highId = Math.max(toId, senderId);
        String key = SINGLE_CONVERSATION_ID.formatted(lowId, highId);
        Object obj = stringRedisTemplate.opsForHash().get(key, "conversationId");
        if (obj == null) {
            conversation = conversationServer.getConversation(toId, senderId, null);
            if (conversation == null) {
                throw new BaseException("获取会话失败", 500);
            }
            conversationId = conversation.getConversationId();
            log.debug("缓存获取失败，查询数据库 conversationId={} oldLastSeq={}", conversationId, conversation.getLastSeq());
        } else {
            conversationId = Long.valueOf(obj.toString());
            try {
                String oldLastSeqStr = stringRedisTemplate.opsForValue().get(SEQ_INC.formatted(conversationId));
                if (oldLastSeqStr == null) {
                    Long dbMaxSeq = messageMapper.selectMaxSeqByConversationId(conversationId);
                    Long oldLastSeq = (dbMaxSeq == null) ? 0L : dbMaxSeq;
                    stringRedisTemplate.opsForValue().setIfAbsent(SEQ_INC.formatted(conversationId), oldLastSeq.toString());
                }
                log.debug("查询到缓存 conversationId={}", conversationId);
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis 连接不可用，触发熔断", e);
                throw e;
            }
        }

        log.debug("toId={} -> sendId={} 获取到会话conversationId={}", toId, senderId, conversationId);
        Long seq = redisIdWorker.msgSeqIncrease(SEQ_INC.formatted(conversationId));
        LocalDateTime now = LocalDateTime.now();
        Long messageId = redisIdWorker.getId(MESSAGE_ID);
        if (timing) {
            log.info("[PERF_ROUTE] clientMessageId={} totalMs={}",
                    clientMessageId, elapsedMs(routeStartedAt));
        }

        Message message = builderMessage(messageId, conversationId, seq, saveOutboxDTO, now);
        Long outboxId = insertMessageOutbox(message, conversationId, seq);

        return SaveMsgVO.builder()
                .messageId(message.getId())
                .reuseMessage(false)
                .seq(seq)
                .outboxId(outboxId)
                .conversationId(conversationId)
                .timeAt(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }

    private Long insertMessageOutbox(Message message, Long conversationId, Long seq) {
        MessagePayloadDTO payloadDTO = BeanUtil.copyProperties(message, MessagePayloadDTO.class);
        String jsonPayload = JSON.toJSONString(payloadDTO);
        MessageOutbox outbox = MessageOutbox.builder()
                .messageId(message.getId())
                .conversationId(conversationId)
                .seq(seq)
                .eventType(EventType.MESSAGE_CREATE.toString())
                .eventKey(MESSAGE_CREATE_EVENT_KEY.formatted(message.getSenderId(),
                        message.getClientMessageId())
                )
                .status(OutboxStatusEnum.PENDING.getCode())
                .retryCount(0)
                .payload(jsonPayload)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        long saveStartedAt = System.nanoTime();
        try {
            this.save(outbox);
        } catch (DuplicateKeyException e) {
            log.warn("消息合子已存在消息 messageId={}", outbox.getMessageId());
            MessageOutbox existing  = getOne(new LambdaQueryWrapper<MessageOutbox>()
                    .eq(MessageOutbox::getEventKey, outbox.getEventKey())
                    .eq(MessageOutbox::getEventType, outbox.getEventType())
            );
            if (existing != null) {
                return existing.getId();
            }
            log.warn("outbox 冲突后回查为空，重试插入 eventKey={}", outbox.getEventKey());
            try {
                this.save(outbox);
            } catch (Exception ex) {
                log.error("outbox 冲突后回试插入失败", ex);
                throw ex;
            }
        } finally {
            if (sampleTiming(message.getClientMessageId())) {
                log.info("[PERF_OUTBOX_SAVE] clientMessageId={} totalMs={}",
                        message.getClientMessageId(),
                        elapsedMs(saveStartedAt));
            }
        }
        return outbox.getId();
    }

    private Message builderMessage(Long messageId, Long conversationId, Long seq, SaveOutboxDTO saveOutboxDTO, LocalDateTime now) {
        now = LocalDateTime.now();
        return Message.builder()
                .id(messageId)
                .clientMessageId(saveOutboxDTO.getClientMessageId())
                .conversationId(conversationId)
                .seq(seq)
                .senderId(saveOutboxDTO.getSenderId())
                .messageType(saveOutboxDTO.getMessageType())
                .textContent(saveOutboxDTO.getTextContent())
                .attachmentId(saveOutboxDTO.getAttachmentId())
                .isBlocked(YesNoStatus.NO.getValue())
                .recalledAt(null)
                .isRecalled(YesNoStatus.NO.getValue())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Override
    @Retryable(
            retryFor = {
                    RuntimeException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 30, maxDelay = 300, multiplier = 2)
    )
    public boolean updateStatus(OutBoxUpdateStatusDTO statusDTO) {

        if (verifyMust(statusDTO)) {
            return false;
        }
        String eventType = statusDTO.getEventType();
        String eventKey = statusDTO.getEventKey();
        log.debug("开始修改outbox 状态 eventType={},eventKey={}", eventType, eventKey);
        MessageOutbox outbox = lambdaQuery()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .one();
        if (outbox == null) {
            log.warn("outbox 不存在 eventType={},eventKey={}", eventType, eventKey);
            throw new RuntimeException("outbox 不存在");
        }
        log.debug("outbox 状态修改成功 eventType={},eventKey={}", eventType, eventKey);
        LocalDateTime updateAt = LocalDateTime.now();
        return lambdaUpdate()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .set(MessageOutbox::getStatus, statusDTO.getStatus())
                .set(MessageOutbox::getPublishedAt, statusDTO.getPublishedAt())
                .set(MessageOutbox::getUpdatedAt, updateAt)
                .set(MessageOutbox::getLockedAt, null)
                .set(MessageOutbox::getLockedBy, null)
                .set(MessageOutbox::getNextRetryAt, null)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatusToRetry(OutBoxUpdateStatusDTO statusDTO) {
        if (verifyMust(statusDTO)) {
            return false;
        }
        String eventType = statusDTO.getEventType();
        String eventKey = statusDTO.getEventKey();

        MessageOutbox outbox = lambdaQuery()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .one();
        if (outbox == null) {
            log.warn("outbox 不存在 ,eventType={},eventKey={}", eventType, eventKey);
            throw new RuntimeException("outbox 不存在");
        }
        if (Objects.equals(outbox.getStatus(), OutboxStatusEnum.SENT.getCode())) {
            return true;
        }
        Integer currentStatus = outbox.getStatus();
        Integer currentRetryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        Integer newRetryCount = currentRetryCount + 1;

        com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper<MessageOutbox> wrapper = lambdaUpdate()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .eq(MessageOutbox::getStatus, currentStatus)
                .set(MessageOutbox::getRetryCount, newRetryCount)
                .set(MessageOutbox::getLastError, statusDTO.getLastError())
                .set(MessageOutbox::getUpdatedAt, LocalDateTime.now())
                .set(MessageOutbox::getLockedAt, null)
                .set(MessageOutbox::getLockedBy, null);
        if (newRetryCount >= messageRetryProperties.getMaxRetryCount()) {
            wrapper.set(MessageOutbox::getStatus, OutboxStatusEnum.FAILED.getCode())
                    .set(MessageOutbox::getNextRetryAt, null);

        } else {

            long nextInterval = getNextInterval(newRetryCount);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(nextInterval * 1_000_000L);
            wrapper.set(MessageOutbox::getStatus, OutboxStatusEnum.RETRY_WAIT.getCode())
                    .set(MessageOutbox::getNextRetryAt, nextRetryAt);
        }
        boolean updateSuccess = wrapper.update();
        if (!updateSuccess) {
            log.warn("更新 outbox 状态失败，可能已被其他线程处理。eventKey={}", eventKey);
        }
        return updateSuccess;
    }

    @Override
    public List<MessageOutbox> getRetryLists() {
        LocalDateTime now = LocalDateTime.now();
        return baseMapper.findDispatchCandidates(now, now.minusMinutes(5), 100);
    }

    @Override
    public boolean getRetryLock(Long outboxId, String lockedBy) {
        return baseMapper.claim(outboxId, lockedBy, LocalDateTime.now().minusMinutes(5)) > 0;
    }

    @Override
    public void unlockRetry(Long outboxId, String lockedBy) {
        lambdaUpdate()
                .eq(MessageOutbox::getId, outboxId)
                .eq(MessageOutbox::getLockedBy, lockedBy)
                .set(MessageOutbox::getLockedAt, null)
                .set(MessageOutbox::getLockedBy, null)
                .set(MessageOutbox::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    private long getNextInterval(Integer retryCount) {
        Integer multiplier = messageRetryProperties.getMultiplier();
        Duration initialInterval = messageRetryProperties.getInitialInterval();
        long initialMs = initialInterval.toMillis();
        long nextInterval;
        if (retryCount <= 1) {
            nextInterval = initialMs;
        } else {
            double calculatedMs = initialMs * Math.pow(multiplier, retryCount - 1);
            long maxIntervalMs = Duration.ofMinutes(10).toMillis();
            nextInterval = Math.min((long) calculatedMs, maxIntervalMs);
        }
        return nextInterval;
    }

    private boolean verifyMust(OutBoxUpdateStatusDTO statusDTO) {
        if (statusDTO == null) {
            return true;
        }
        String eventType = statusDTO.getEventType();
        String eventKey = statusDTO.getEventKey();
        if (eventType == null || eventKey == null) {
            log.error("outbox 状态修改失败 eventType={},eventKey={}", eventType, eventKey);
            return true;
        }
        return false;
    }

    private static boolean sampleTiming(String clientMessageId) {
        return clientMessageId != null
                && Math.floorMod(clientMessageId.hashCode(), 100) == 0;
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}