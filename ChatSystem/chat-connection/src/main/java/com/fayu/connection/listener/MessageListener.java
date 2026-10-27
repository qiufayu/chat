package com.fayu.connection.listener;

import com.fayu.common.exception.BaseException;
import com.fayu.connection.domain.vo.ChatResponseMessageVO;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener {
    private final MessageUtil messageUtil;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = "",durable = "true"),
                    exchange = @Exchange(name = "",type = "topic"),
                    key = {}
            )
    )
    public void sendMessageListener(ChatResponseMessageVO messageVO){
        SingleSession session = SingleSessionFactory.getSession();
        Long toId = messageVO.getToId();
        Long sendId = messageVO.getSendId();
        if (toId == null ||sendId == null ){
            throw  new BaseException("服务错误，接收id不可以为空",500);
        }
        Channel channel = session.getChannel(toId);
        if (channel == null || !channel.isOpen()){
            throw new BaseException("接收者已下线",500);
        }
        String json = messageUtil.toJson(messageVO);
        ChannelFuture channelFuture = channel.writeAndFlush(new TextWebSocketFrame(json));
        channelFuture.addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                Channel sendChannel = session.getChannel(sendId);
                if (sendChannel != null && sendChannel.isOpen()){
                    // TODO 把错误消息推送到离线消息
                }
            }
        });

    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = "",durable = "true"),
                    exchange = @Exchange(name = "",type = "topic"),
                    key = {}
            )
    )
    public void offMessageListener(){
        log.debug("离线消息保存成功");
    }

}
