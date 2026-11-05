package com.fayu.message.server.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.dto.SaveMsgAndUpdateConversationDTO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.message.config.MessageRetryProperties;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.message.domain.dto.AdvanceDeviceCursorDTO;
import com.fayu.message.domain.dto.MessagePayloadDTO;
import com.fayu.common.domain.dto.SaveOutboxDTO;
import com.fayu.message.domain.dto.UpdateConversationDTO;
import com.fayu.message.domain.po.Conversation;
import com.fayu.message.domain.po.Message;
import com.fayu.message.domain.po.MessageInfoRow;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.vo.ConversationVO;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.message.enums.EventType;
import com.fayu.message.enums.MessageContentType;
import com.fayu.message.enums.OutboxStatusEnum;
import com.fayu.message.enums.YesNoStatus;
import com.fayu.message.mapper.MessageMapper;
import com.fayu.message.mapper.DeviceSyncCursorMapper;
import com.fayu.message.server.IConversationServer;
import com.fayu.message.server.IMessageOutboxServer;
import com.fayu.message.server.IMessageServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.fayu.common.constant.MessageEventKey.MESSAGE_CREATE_EVENT_KEY;
import static com.fayu.common.constant.RabbitmqConstant.*;
import static com.fayu.common.constant.RedisConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServerImpl extends ServiceImpl<MessageMapper, Message> implements IMessageServer {

    private final IConversationServer conversationServer;
    private final RedisIdWorker redisIdWorker;
    private final MessageRetryProperties messageRetryProperties;
    private final IMessageOutboxServer messageOutboxServer;
    private final MessageMapper messageMapper;
    private final DeviceSyncCursorMapper deviceSyncCursorMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    @Override
    public SaveMsgVO saveOutbox(SaveOutboxDTO saveOutboxDTO) {
//        Long userId = UserContext.getUser();
//        if (userId == null || userId.equals(saveMsgDTO.getSenderId())){
//            throw new BaseException("业务错误",500);
//        }

        Long toId = saveOutboxDTO.getToId();
        Long senderId = saveOutboxDTO.getSenderId();
        String clientMessageId = saveOutboxDTO.getClientMessageId();
        boolean timing = sampleTiming(clientMessageId);

        // 查询消息是否存在
        long queryStartedAt = System.nanoTime();
        Message message = lambdaQuery()
                .eq(Message::getSenderId, senderId)
                .eq(Message::getClientMessageId, clientMessageId)
                .one();

        if (timing) {
            log.info("[PERF_QUERY] clientMessageId={} totalMs={}",
                    clientMessageId, elapsedMs(queryStartedAt));
        }

        long timeAt;
        if (message != null){
            timeAt = message.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            log.debug("重复消息 messageId = {}",message.getId());
            // 客户端使用相同 clientMessageId 重试时，当前设备已经知道这条消息已保存，
            // 不应因为重试请求而再次从同步接口拉取自己的消息。
//            if (saveMsgDTO.getDeviceId() != null && !saveMsgDTO.getDeviceId().isBlank()) {
//                deviceSyncCursorMapper.advance(senderId, saveMsgDTO.getDeviceId(),
//                        message.getConversationId(), message.getSeq());
//            }
            return SaveMsgVO.builder()
                    .reuseMessage(true)
                    .messageId(message.getId())
                    .seq(message.getSeq())
                    .conversationId(message.getConversationId())
                    .reuseMessage(true)
                    .timeAt(timeAt)
                    .build();
        }

        long routeStartedAt = System.nanoTime();

        ConversationVO conversation;
        Long conversationId;
        Long oldLastSeq;
        Long lowId = Math.min(toId,senderId);
        Long highId = Math.max(toId,senderId);
        String key = SINGLE_CONVERSATION_ID.formatted(lowId,highId);
        Object obj = stringRedisTemplate.opsForHash().get(key, "conversationId");
        if (obj == null){
            conversation = conversationServer.getConversation(toId, senderId, null);
            if (conversation == null){
                throw new BaseException("获取会话失败",500);
            }
            conversationId =conversation.getConversationId();
            oldLastSeq = conversation.getLastSeq();
            log.debug("缓存获取失败，查询数据库 conversationId={} oldLastSeq={}",conversationId,oldLastSeq);
        }else {
            conversationId = Long.valueOf(obj.toString());
            try {
                String oldLastSeqStr = stringRedisTemplate.opsForValue().get(SEQ_INC.formatted(conversationId));
                if (oldLastSeqStr == null) {
                    Long dbMaxSeq = messageMapper.selectMaxSeqByConversationId(conversationId);
                    oldLastSeq = (dbMaxSeq == null) ? 0L : dbMaxSeq;
                    // 顺便把查到的值写回 Redis，防止下次再查库
                    stringRedisTemplate.opsForValue().setIfAbsent(SEQ_INC.formatted(conversationId), oldLastSeq.toString());
                } else {
                    oldLastSeq = Long.parseLong(oldLastSeqStr);
                }
                log.debug("查询到缓存 conversationId={} oldLastSeq={}",conversationId,oldLastSeq);
            } catch (RedisConnectionFailureException e) {
                // Redis 连接失败（下线、网络不通、端口错误等），执行你的兜底逻辑
                log.warn("Redis 连接不可用，触发熔断", e);
                throw e;
            }
        }

        log.debug("toId={} -> sendId={} 获取到会话conversationId={}",toId,senderId,conversationId);
        Long seq = redisIdWorker.msgSeqIncrease(SEQ_INC.formatted(conversationId));
        LocalDateTime now = LocalDateTime.now();
        Long messageId = redisIdWorker.getId(MESSAGE_ID);
        if (timing) {
            log.info("[PERF_ROUTE] clientMessageId={} totalMs={}",
                    clientMessageId, elapsedMs(routeStartedAt));
        }

        message = builderMessage(messageId,conversationId, seq, saveOutboxDTO, now);
        // 异步创建会话和outbox
        Long outboxId = insertMessageOutbox(message, conversationId, seq);
//        if (saveMsgDTO.getDeviceId() != null && !saveMsgDTO.getDeviceId().isBlank()) {
//            sendAdvanceDeviceCursorEvent(senderId, saveMsgDTO.getDeviceId(), conversationId, seq);
//        }

        return SaveMsgVO.builder()
                .messageId(message.getId())
                .reuseMessage(false)
                .seq(seq)
                .outboxId(outboxId)
                .conversationId(conversationId)
                .timeAt(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }

    private void sendMessageCreateEvent(Message message, Long senderId, String clientMessageId) {
        try {
//            rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_CREATE_MESSAGE,message);
            boolean save = save(message);
        } catch (DuplicateKeyException e) {
            log.warn("消息已存在 sendId={},client_message_id={}", senderId, clientMessageId);
            throw e;
        }
    }

    private void sendAdvanceDeviceCursorEvent(Long userId, String deviceId, Long conversationId, Long seq) {
        AdvanceDeviceCursorDTO event = AdvanceDeviceCursorDTO.builder()
                .userId(userId)
                .deviceId(deviceId)
                .conversationId(conversationId)
                .seq(seq)
                .build();
        rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE, KEY_ADVANCE_DEVICE_CURSOR, event);
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
//        rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_CREATE_OUTBOX,outbox);

        long saveStartedAt = System.nanoTime();
        try {
            messageOutboxServer.save(outbox);
        } catch (DuplicateKeyException e) {
            log.warn("消息合子已存在消息 messageId={}", outbox.getMessageId());
        }finally {
            if (sampleTiming(message.getClientMessageId())) {
                log.info("[PERF_OUTBOX_SAVE] clientMessageId={} totalMs={}",
                        message.getClientMessageId(),
                        elapsedMs(saveStartedAt));
            }
        }
        return outbox.getId();
    }

    private void sendConversationUpdateEvent(Long conversationId, Long oldLastSeq, Long seq, Message message, LocalDateTime now) {
        UpdateConversationDTO conversationDTO = UpdateConversationDTO.builder()
                .conversationId(conversationId)
                .oldLastSeq(oldLastSeq)
                .newLastSeq(seq)
                .lastMessageId(message.getId())
                .lastMessageAt(now)
                .build();
        rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_UPDATE_CONVERSATION,conversationDTO);
    }

    private Message builderMessage(Long messageId, Long conversationId, Long seq, SaveOutboxDTO saveOutboxDTO, LocalDateTime now) {
        now = LocalDateTime.now();
        return  Message.builder()
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
    public  ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(Long conversationId, Long seq) {
        MessageInfoRow row = messageMapper.getMessageInfo(conversationId,seq);
        if (row == null) return null;

        ChatResponseMessageVO<ChatRequestMessageDTO> vo = new ChatResponseMessageVO<>();
        vo.setMessageId(row.getMessageId());
        vo.setConversationId(row.getConversationId());
        vo.setSeq(row.getSeq());
        vo.setConversationType(row.getConversationType());
        vo.setSendId(row.getSendId());
        vo.setToId(row.getToId());
        vo.setGroupId(row.getGroupId());
        vo.setMessageType(row.getMessageType());

        // payload 统一是 ChatRequestMessageDTO，content 取文本内容
        ChatRequestMessageDTO payload = new ChatRequestMessageDTO();
        payload.setContent(row.getTextContent());   // 图片/文件可放 attachmentId 或 url
        vo.setPayload(payload);

        // 撤回 / 违规
        if (row.getIsRecalled() != null && row.getIsRecalled() == 1) {
            vo.setErrorContent("消息已撤回");
        }
        if (row.getIsBlocked() != null && row.getIsBlocked() == 1) {
            vo.setErrorContent("消息含违规内容");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMsgAndUpdateConversation(SaveMsgAndUpdateConversationDTO dto) {
        if (dto == null) return;

        Long messageId = dto.getMessageId();
        if (ObjUtil.isEmpty(messageId)){
            return;
        }
        Message message = messageMapper.selectById(messageId);
        if (message != null){
            log.warn("消息已存在 messageId={}", messageId);
            return;
        }

        JsonNode payload = dto.getPayload();
        Integer messageType = dto.getMessageType();
        String textContent = messageType == MessageContentType.TEXT.getValue() ? payload.get("textContent").asText() : null;
        Long attachmentId = messageType == MessageContentType.IMAGE.getValue() || messageType == MessageContentType.FILE.getValue() ? payload.get("attachmentId").asLong() : null;
        message = Message.builder()
                .id(messageId)
                .seq(dto.getSeq())
                .senderId(dto.getSendId())
                .clientMessageId(dto.getClientMessageId())
                .conversationId(dto.getConversationId())
                .messageType(messageType)
                .textContent(textContent)
                .attachmentId(attachmentId)
                .isBlocked(YesNoStatus.NO.getValue())
                .recalledAt(null)
                .isRecalled(YesNoStatus.NO.getValue())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        boolean inserted = false;
        try {
            inserted = messageMapper.insert(message) > 0;
        } catch (DuplicateKeyException e) {
            log.warn("消息已存在", e);
            return;
        }
        UpdateConversationDTO conversationDTO = UpdateConversationDTO.builder()
                .conversationId(dto.getConversationId())
                .newLastSeq(dto.getSeq())
                .lastMessageId(message.getId())
                .lastMessageAt(message.getCreatedAt())
                .build();
        conversationServer.updateConversation(conversationDTO);
    }

    private static boolean sampleTiming(String clientMessageId) {
        return clientMessageId != null
                && Math.floorMod(clientMessageId.hashCode(), 100) == 0;
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private void updateStatusToRetry(String eventType, String eventKey,String reason){
        messageOutboxServer.updateStatusToRetry(OutBoxUpdateStatusDTO.builder()
                        .eventKey(eventKey)
                        .eventType(eventType)
                        .lastError(reason)
                        .build());
    }
    private String getEventKey(Long sendId,String clientMessageId){
        return MESSAGE_CREATE_EVENT_KEY.formatted(sendId,clientMessageId);
    }

}
