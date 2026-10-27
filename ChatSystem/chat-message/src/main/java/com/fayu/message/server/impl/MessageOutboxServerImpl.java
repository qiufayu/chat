package com.fayu.message.server.impl;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fayu.message.config.MessageRetryProperties;
import com.fayu.message.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.message.domain.po.EventEnvelope;
import com.fayu.message.domain.po.MessageMetadata;
import com.fayu.message.domain.po.MessageOutbox;
import com.fayu.message.enums.OutboxStatusEnum;
import com.fayu.message.mapper.MessageMapper;
import com.fayu.message.mapper.MessageOutboxMapper;
import com.fayu.message.server.IMessageOutboxServer;
import com.fayu.message.server.IMessageServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final MessageMapper messageMapper;
    @Override
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
        return lambdaUpdate()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType)
                .update();
    }

    @Override
    public boolean retryMessageUpdateStatus(OutBoxUpdateStatusDTO statusDTO) {
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
        Integer status = outbox.getStatus();
        Integer retryCount = outbox.getRetryCount();

        if (!Objects.equals(status, OutboxStatusEnum.SENDING.getCode())){
            return false;
        }

        LambdaUpdateChainWrapper<MessageOutbox> wrapper = lambdaUpdate()
                .eq(MessageOutbox::getEventKey, eventKey)
                .eq(MessageOutbox::getEventType, eventType);
        // 达到最大重试次数
        if (Objects.equals(retryCount, messageRetryProperties.getMaxRetryCount())){
            wrapper.set(MessageOutbox::getStatus,OutboxStatusEnum.FAILED.getCode())
                    .set(MessageOutbox::getNextRetryAt,null)
                    .set(MessageOutbox::getLastError,statusDTO.getLastError());
        }

        if (Objects.equals(status, OutboxStatusEnum.SENDING.getCode()) || status.equals(OutboxStatusEnum.PENDING.getCode())){
            long nextInterval = getNextInterval(retryCount);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(nextInterval);
            wrapper.set(MessageOutbox::getStatus,OutboxStatusEnum.RETRY_WAIT.getCode())
                    .set(MessageOutbox::getNextRetryAt,nextRetryAt)
                    .set(MessageOutbox::getLastError,statusDTO.getLastError());
        }
        return wrapper.set(MessageOutbox::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    @Override
    public List<MessageOutbox> getRetryLists() {
        lambdaQuery()
                .le(MessageOutbox::getNextRetryAt, LocalDateTime.now())
                .isNull(MessageOutbox::getLockedAt)
                .eq(MessageOutbox::getStatus, OutboxStatusEnum.RETRY_WAIT.getCode())
                .select(MessageOutbox::getId)
                .last("limit 100")
                .list()
                .stream();
        return List.of();
    }

    private long getNextInterval(Integer retryCount) {
        Integer multiplier = messageRetryProperties.getMultiplier();
        Duration initialInterval = messageRetryProperties.getInitialInterval();
        long initialMs = initialInterval.toMillis();
        long nextInterval;
        // 第一次设置初始的重试时间
        if (retryCount == 1){
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