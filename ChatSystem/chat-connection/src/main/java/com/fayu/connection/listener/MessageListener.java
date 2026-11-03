package com.fayu.connection.listener;

import cn.hutool.core.bean.BeanUtil;
import com.fayu.common.domain.po.EventEnvelope;
import com.fayu.common.domain.po.MessageMetadata;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.SingleSession;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;

import static com.fayu.common.constant.RabbitmqConstant.KEY_SEND_MESSAGE;
import static com.fayu.common.constant.RabbitmqConstant.KEY_SENDER_SELF_SYNC;
import static com.fayu.common.constant.RabbitmqConstant.MESSAGE_EXCHANGE;
import static com.fayu.common.constant.RabbitmqConstant.SEND_MESSAGE_QUEUE;
import static com.fayu.common.constant.RabbitmqConstant.SENDER_SELF_SYNC_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener {

    private final MessageUtil messageUtil;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = SEND_MESSAGE_QUEUE, durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_SEND_MESSAGE}
            )
    )
    public void sendMessageListener(EventEnvelope<?> eventEnvelope) {
        log.info("收到消息事件: {}", eventEnvelope);
        if (eventEnvelope == null || eventEnvelope.getMetadata() == null) {
            log.warn("收到空消息事件，忽略处理");
            return;
        }
        MessageMetadata metadata = eventEnvelope.getMetadata();
        Long toId = metadata.getToId();
        Long sendId = metadata.getSendId();
        if (toId == null || metadata.getMessageId() == null) {
            log.warn("消息事件缺少接收者或消息编号，忽略处理: {}", eventEnvelope);
            return;
        }

        SingleSession session = SingleSessionFactory.getSession();
        Collection<Channel> toChannels = session.getChannels(toId);
        if (toChannels.isEmpty()) {
            log.debug("用户 {} 不在线，等待重连同步 messageId={}", toId, metadata.getMessageId());
            return;
        }

        ChatResponseMessageVO<Object> messageVO = new ChatResponseMessageVO<>();
        BeanUtil.copyProperties(metadata, messageVO);
        messageVO.setConversationId(metadata.getConversationId());
        messageVO.setMessageType(metadata.getMessageType());
        messageVO.setPayload(eventEnvelope.getPayload());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "MESSAGE");
        response.put("messageId", messageVO.getMessageId());
        response.put("conversationId", messageVO.getConversationId());
        response.put("seq", messageVO.getSeq());
        response.put("sendId", messageVO.getSendId());
        response.put("senderId", messageVO.getSendId());
        response.put("toId", messageVO.getToId());
        response.put("messageType", messageVO.getMessageType());
        response.put("payload", messageVO.getPayload());
        String json = messageUtil.toJson(response);
        for (Channel channel : toChannels) {
            if (!channel.isOpen()) {
                continue;
            }
            channel.writeAndFlush(new TextWebSocketFrame(json)).addListener(future -> {
                if (!future.isSuccess()) {
                    log.warn("在线消息写入通道失败，等待设备游标补拉 messageId={}",
                            metadata.getMessageId(), future.cause());
                }
            });
        }
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = SENDER_SELF_SYNC_QUEUE, durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_SENDER_SELF_SYNC}
            )
    )
    public void senderSelfSyncListener(EventEnvelope<?> eventEnvelope) {
        log.info("收到发送者自同步事件: {}", eventEnvelope);
        if (eventEnvelope == null || eventEnvelope.getMetadata() == null) {
            log.warn("收到发送者自同步空事件，忽略处理");
            return;
        }
        MessageMetadata metadata = eventEnvelope.getMetadata();
        Long sendId = metadata.getSendId();
        if (sendId == null || metadata.getMessageId() == null) {
            log.warn("发送者自同步事件缺少发送者或消息编号，忽略处理: {}", eventEnvelope);
            return;
        }

        SingleSession session = SingleSessionFactory.getSession();
        Collection<Channel> senderChannels = session.getChannels(sendId);
        if (senderChannels.isEmpty()) {
            return;
        }

        ChatResponseMessageVO<Object> messageVO = new ChatResponseMessageVO<>();
        BeanUtil.copyProperties(metadata, messageVO);
        messageVO.setConversationId(metadata.getConversationId());
        messageVO.setMessageType(metadata.getMessageType());
        messageVO.setPayload(eventEnvelope.getPayload());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "MESSAGE");
        response.put("messageId", messageVO.getMessageId());
        response.put("clientMessageId", metadata.getClientMessageId());
        response.put("conversationId", messageVO.getConversationId());
        response.put("seq", messageVO.getSeq());
        response.put("sendId", messageVO.getSendId());
        response.put("senderId", messageVO.getSendId());
        response.put("toId", messageVO.getToId());
        response.put("messageType", messageVO.getMessageType());
        response.put("payload", messageVO.getPayload());
        response.put("timestamp", metadata.getTimestamp());
        String json = messageUtil.toJson(response);

        for (Channel channel : senderChannels) {
            if (!channel.isOpen()) {
                continue;
            }
            String channelDeviceId = session.getDeviceId(channel);
            if (channelDeviceId != null && channelDeviceId.equals(metadata.getDeviceId())) {
                continue;
            }
            channel.writeAndFlush(new TextWebSocketFrame(json)).addListener(future -> {
                if (!future.isSuccess()) {
                    log.warn("自同步到发送者其他设备失败 messageId={}, deviceId={}",
                            metadata.getMessageId(), channelDeviceId, future.cause());
                }
            });
        }
    }
}