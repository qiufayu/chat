package com.fayu.message.server.impl;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fayu.message.config.MessageRetryProperties;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.message.enums.OutboxStatusEnum;
import com.fayu.message.mapper.MessageOutboxMapper;
import com.fayu.message.server.IMessageOutboxServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 消息可靠投递事件表 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageOutboxServerImpl extends ServiceImpl<MessageOutboxMapper, MessageOutbox> implements IMessageOutboxServer {

    private final MessageRetryProperties messageRetryProperties;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(OutBoxUpdateStatusDTO statusDTO) {

        if ( verifyMust(statusDTO)){
            return false;
        }
        String eventType = statusDTO.getEventType();
        String eventKey = statusDTO.getEventKey();
        log.debug("开始修改outbox 状态 eventType={},eventKey={}",eventType,eventKey);
        MessageOutbox outbox = lambdaQuery()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .one();
        if (outbox == null){
            log.error("outbox 不存在 eventType={},eventKey={}",eventType,eventKey);
            return false;
        }
        log.debug("outbox 状态修改成功 eventType={},eventKey={}",eventType,eventKey);
        LocalDateTime updateAt = LocalDateTime.now();
        return lambdaUpdate()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .set(MessageOutbox::getStatus,statusDTO.getStatus())
                .set(MessageOutbox::getPublishedAt,statusDTO.getPublishedAt())
                .set(MessageOutbox::getUpdatedAt,updateAt)
                .set(MessageOutbox::getLockedAt,null)
                .set(MessageOutbox::getLockedBy,null)
                .set(MessageOutbox::getNextRetryAt,null)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatusToRetry(OutBoxUpdateStatusDTO statusDTO) {
        if (verifyMust(statusDTO)){
            return false;
        }
        String eventType = statusDTO.getEventType();
        String eventKey = statusDTO.getEventKey();

        MessageOutbox outbox = lambdaQuery()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .one();
        if (outbox == null){
            log.error("outbox 不存在 ,eventType={},eventKey={}",eventType,eventKey);
            return false;
        }
        if (Objects.equals(outbox.getStatus(), OutboxStatusEnum.SENT.getCode())) {
            // 迟到的失败回调不能把已经确认成功的事件重新置为重试。
            return true;
        }
        Integer currentStatus = outbox.getStatus();
        Integer currentRetryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        Integer newRetryCount = currentRetryCount + 1;

        LambdaUpdateChainWrapper<MessageOutbox> wrapper = lambdaUpdate()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .eq(MessageOutbox::getStatus, currentStatus)
                .set(MessageOutbox::getRetryCount, newRetryCount)
                .set(MessageOutbox::getLastError, statusDTO.getLastError())
                .set(MessageOutbox::getUpdatedAt, LocalDateTime.now())
                .set(MessageOutbox::getLockedAt, null)
                .set(MessageOutbox::getLockedBy, null);
        // 达到最大重试次数
        if (newRetryCount >= messageRetryProperties.getMaxRetryCount()){
            wrapper.set(MessageOutbox::getStatus,OutboxStatusEnum.FAILED.getCode())
                    .set(MessageOutbox::getNextRetryAt,null);

        }else {

            long nextInterval = getNextInterval(newRetryCount);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(nextInterval * 1_000_000L);
            wrapper.set(MessageOutbox::getStatus,OutboxStatusEnum.RETRY_WAIT.getCode())
                    .set(MessageOutbox::getNextRetryAt,nextRetryAt);
        }
        // 执行更新
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
                .eq(MessageOutbox::getId,outboxId)
                .eq(MessageOutbox::getLockedBy,lockedBy)
                .set(MessageOutbox::getLockedAt,null)
                .set(MessageOutbox::getLockedBy,null)
                .set(MessageOutbox::getUpdatedAt,LocalDateTime.now())
                .update();
    }
    private long getNextInterval(Integer retryCount) {
        Integer multiplier = messageRetryProperties.getMultiplier();
        Duration initialInterval = messageRetryProperties.getInitialInterval();
        long initialMs = initialInterval.toMillis();
        long nextInterval;
        // 第一次设置初始的重试时间
        if (retryCount <= 1){
            nextInterval = initialMs;
        }else{
            double calculatedMs = initialMs * Math.pow(multiplier, retryCount - 1);
            long maxIntervalMs = Duration.ofMinutes(10).toMillis();
            nextInterval = Math.min((long) calculatedMs, maxIntervalMs);
        }
        return nextInterval;
    }
    private boolean verifyMust(OutBoxUpdateStatusDTO statusDTO){
        if (statusDTO == null){
            return true;
        }
        String eventType = statusDTO.getEventType();
        String eventKey = statusDTO.getEventKey();
        if (eventType == null || eventKey == null){
            log.error("outbox 状态修改失败 eventType={},eventKey={}",eventType,eventKey);
            return true;
        }
        return false;
    }
}
