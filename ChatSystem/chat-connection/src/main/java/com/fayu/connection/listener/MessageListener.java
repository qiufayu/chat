package com.fayu.connection.listener;

import cn.hutool.core.bean.BeanUtil;
import com.fayu.api.client.MsgClient;
import com.fayu.api.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.api.domain.po.EventEnvelope;
import com.fayu.api.domain.po.MessageMetadata;
import com.fayu.common.constant.RabbitmqConstant;
import com.fayu.common.exception.BaseException;
import com.fayu.connection.domain.dto.SaveOffMessageBoxDTO;
import com.fayu.connection.domain.vo.ChatResponseMessageVO;
import com.fayu.connection.enums.EventType;
import com.fayu.connection.enums.OffMsgStatus;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.fayu.common.constant.MessageEventKey.MESSAGE_CREATE_EVENT_KEY;
import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener {
    private final MessageUtil messageUtil;
    private final MsgClient msgClient;
    private final RabbitTemplate rabbitTemplate;
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
        String eventKey = MESSAGE_CREATE_EVENT_KEY.formatted(messageId);
        Channel channel = session.getChannel(toId);
        if (channel != null || !channel.isOpen()){
            log.info("添加重试信息......");
            updateStatusToRetry(metadata, eventKey, new BaseException("报错了",500));
//            log.debug("用户 {} 不在线，消息转入离线存储或等待重试", toId);
//            saveOffMsgToBox(metadata, toId);
            return;
        }
        Object payload = eventEnvelope.getPayload();
        Long conversationId = metadata.getConversationId();
        Integer messageType = metadata.getMessageType();
        ChatResponseMessageVO<Object> messageVO = new ChatResponseMessageVO<>();
        BeanUtil.copyProperties(metadata, messageVO);
        messageVO.setPayload(payload);
        messageVO.setConversationId(conversationId);
        messageVO.setMessageType(messageType);
        String json = messageUtil.toJson(messageVO);
        ChannelFuture channelFuture = channel.writeAndFlush(new TextWebSocketFrame(json))
                .addListener(
                future -> {
                    if (future.isSuccess()){

                        updateStatusToPass(metadata, eventKey);
                    }else{
                        Throwable cause = future.cause();
                        log.error("消息推送失败，messageId={}，准备重试", messageId, cause);
                        updateStatusToRetry(metadata, eventKey, cause);
                    }
                }
        );

    }

//    @RabbitListener(
//            bindings = @QueueBinding(
//                    value = @Queue(name = GET_OFF_MESSAGE_QUEUE,durable = "true"),
//                    exchange = @Exchange(name = MESSAGE_EXCHANGE,type = "topic"),
//                    key = {KEY_GET_OFF_MESSAGE}
//            )
//    )
//    public void sendOffMessageListener(Long userId){
//        while(true){
//            msgClient.
//        }
//    }

    private void saveOffMsgToBox(MessageMetadata metadata, Long toId) {
        SaveOffMessageBoxDTO offMessageBoxDTO = SaveOffMessageBoxDTO.builder()
                .messageId(metadata.getMessageId())
                .userId(toId)
                .conversationId(metadata.getConversationId())
                .createdAt(LocalDateTime.now())
                .status(OffMsgStatus.PENDING.getCode())
                .build();
        rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_SAVE_OFF_MESSAGE,offMessageBoxDTO);
    }

    private void updateStatusToPass(MessageMetadata metadata, String eventKey) {
        OutBoxUpdateStatusDTO statusDTO = OutBoxUpdateStatusDTO.builder()
                .eventType(metadata.getEventType())
                .eventKey(eventKey)
                .status(OutboxStatusEnum.SENT.getCode())
                .publishedAt(LocalDateTime.now())
                .build();
        // 修改outbox状态
        msgClient.updateStatus(statusDTO);
    }
    private void updateStatusToRetry(MessageMetadata metadata, String eventKey, Throwable lastError) {
        OutBoxUpdateStatusDTO statusDTO = OutBoxUpdateStatusDTO.builder()
                .eventType(metadata.getEventType())
                .eventKey(eventKey)
                .lastError(lastError.getMessage())
                .publishedAt(LocalDateTime.now())
                .build();
        // 修改outbox状态
        msgClient.updateStatusToRetry(statusDTO);
    }



}
