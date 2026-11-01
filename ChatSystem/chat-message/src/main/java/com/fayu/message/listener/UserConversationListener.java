package com.fayu.message.listener;

import com.fayu.message.domain.po.UserConversation;
import com.fayu.message.server.IUserConversationServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserConversationListener {
    private final IUserConversationServer userConversationServer;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = USER_CONVERSATION_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_CREATE_USER_CONVERSATION}
            )
    )
    public void createUserConversation(ArrayList<UserConversation> userConversations){
        boolean pass = userConversationServer.saveBatch(userConversations);
        if (!pass){
            log.error("批量插入用户会话失败，userConversations={}",userConversations);
        }
    }
}
