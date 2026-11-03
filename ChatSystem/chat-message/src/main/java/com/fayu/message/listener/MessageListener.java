package com.fayu.message.listener;

import com.fayu.message.domain.po.Message;
import com.fayu.message.server.IMessageServer;
import jakarta.validation.ConstraintViolationException;
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
public class MessageListener {

    private final IMessageServer messageServer;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = MESSAGE_QUEUE, durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_CREATE_MESSAGE}
            )
    )
    public void createMessageListener(Message message){
        try {
            boolean save = messageServer.save(message);
        } catch (DuplicateKeyException | ConstraintViolationException e) {
            throw new RuntimeException(e);
        }
    }

}
