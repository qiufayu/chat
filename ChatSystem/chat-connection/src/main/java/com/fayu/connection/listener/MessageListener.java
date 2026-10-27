package com.fayu.connection.listener;

import cn.hutool.core.bean.BeanUtil;
import com.fayu.api.client.MsgClient;
import com.fayu.api.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.api.domain.po.EventEnvelope;
import com.fayu.api.domain.po.MessageMetadata;
import com.fayu.common.exception.BaseException;
import com.fayu.connection.domain.vo.ChatResponseMessageVO;
import com.fayu.connection.enums.EventType;
import com.fayu.connection.enums.OutboxStatusEnum;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.SingleSession;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.MessageEventKey.MESSAGE_CREATE_EVENT_KEY;
import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener {
    private final MessageUtil messageUtil;
    private final MsgClient msgClient;
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = SEND_MESSAGE_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE,type = "topic"),
                    key = {KEY_SEND_MESSAGE}
            )
    )
    public void sendMessageListener(EventEnvelope<?> eventEnvelope){
        log.debug("mq 队列{}获取到消息{}，开始执行",SEND_MESSAGE_QUEUE,eventEnvelope);
        SingleSession session = SingleSessionFactory.getSession();

        MessageMetadata metadata = eventEnvelope.getMetadata();
        Long toId = metadata.getToId();
        Long sendId = metadata.getSendId();
        Long messageId = metadata.getMessageId();
        if (toId == null ||sendId == null || messageId == null){
            throw  new BaseException("服务错误，接收id不可以为空",500);
        }
        Channel channel = session.getChannel(toId);
        if (channel == null || !channel.isOpen()){
            log.debug("用户 {} 不在线，消息转入离线存储或等待重试", toId);

            return;
        }

        Object payload = eventEnvelope.getPayload();
        ChatResponseMessageVO<Object> messageVO = new ChatResponseMessageVO<>();
        BeanUtil.copyProperties(metadata, messageVO);
        messageVO.setPayload(payload);

        String json = messageUtil.toJson(messageVO);

        // 修改outbox状态
        boolean pass = outboxMessageStateUpdate(eventEnvelope);
        if (!pass){
            log.error("消息推送失败，messageId={}，准备重试", messageId);
            outboxMessageStateUpdate(eventEnvelope);
        }
        ChannelFuture channelFuture = channel.writeAndFlush(new TextWebSocketFrame(json));
        channelFuture.addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                if (future.isSuccess()){

                    boolean pass = outboxMessageStateUpdate(eventEnvelope);
                    if (pass){
                        log.debug("消息推送成功，messageId={}", messageId);
                    }
                }else{
                    Throwable cause = future.cause();
                    log.error("消息推送失败，messageId={}，准备重试", messageId, cause);
                    outboxMessageStateUpdate(eventEnvelope);
                }
            }
        });

    }


    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = RETRY_MESSAGE_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE,type = "topic"),
                    key = {KEY_RETRY_MESSAGE}
            )
    )
    public void retryMessageListener(){
        log.debug("消息重试");
    }


    private boolean outboxMessageStateUpdate(EventEnvelope<?> eventEnvelope) {
        return msgClient.updateStatus(eventEnvelope);
    }

}
