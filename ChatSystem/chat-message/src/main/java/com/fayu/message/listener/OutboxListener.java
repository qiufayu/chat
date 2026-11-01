package com.fayu.message.listener;

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
                    value = @Queue(name = OUTBOX_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KET_CREATE_OUTBOX}
            )
    )
    public void createOutboxListener(MessageOutbox outbox){
        try {
            messageOutboxServer.save(outbox);
        } catch (DuplicateKeyException e) {
            log.warn("消息合子已存在消息 messageId={}", outbox.getMessageId());
        }
    }
}
