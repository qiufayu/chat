package com.fayu.message.listener;

import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.message.server.IMessageOutboxServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxListener {
    private final IMessageOutboxServer messageOutboxServer;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = OUTBOX_CREATE_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_CREATE_OUTBOX}
            )
    )
    public void createOutboxListener(MessageOutbox outbox){
        try {
            log.info("【mq】 创建消息盒子 messageId={}",outbox.getMessageId());
            messageOutboxServer.save(outbox);
        } catch (DuplicateKeyException e) {
            log.warn("消息合子已存在消息 messageId={}", outbox.getMessageId());
        }
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = OUTBOX_SENT_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_UPDATE_STATUS}
            )
    )
    public void updateStatusListener(OutBoxUpdateStatusDTO statusDTO) throws InterruptedException {
        try {
            log.info("【mq】 更新消息盒子状态 eventType={}",statusDTO.getEventType());
            messageOutboxServer.updateStatus(statusDTO);
        } catch (Exception e) {
            log.warn("更新消息盒子状态失败 触发重试 eventType={}", statusDTO.getEventType());
            throw e;
        }
    }
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = OUTBOX_RETRY_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_UPDATE_STATUS_TO_RETRY}
            )
    )
    public void updateStatusToRetryListener(OutBoxUpdateStatusDTO statusDTO){
        try {
            log.info("【mq】 更新消息盒子状态为重试 eventType={}",statusDTO.getEventType());
            messageOutboxServer.updateStatusToRetry(statusDTO);
        } catch (Exception e) {
            log.error("更新消息盒子状态为重试失败 eventType={}", statusDTO.getEventType(), e);
        }
    }
}