package com.fayu.message.listener;

import com.fayu.message.domain.dto.UpdateConversationDTO;
import com.fayu.message.server.IConversationServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationListener {

    private final IConversationServer conversationServer;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = CONVERSATION_QUEUE,declare = "true",durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE,type = ExchangeTypes.TOPIC),
                    key = {KEY_UPDATE_CONVERSATION}
            )
    )
    public void updateConversationListener(UpdateConversationDTO dto){
        try {
            conversationServer.updateConversation(dto);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
