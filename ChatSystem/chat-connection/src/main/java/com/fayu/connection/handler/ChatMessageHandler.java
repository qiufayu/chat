package com.fayu.connection.handler;

import com.fayu.api.client.MsgClient;
import com.fayu.common.domain.dto.*;
import com.fayu.common.domain.po.EventEnvelope;
import com.fayu.common.domain.po.MessageMetadata;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.common.domain.vo.SyncBatchVO;
import com.fayu.common.exception.BaseException;
import com.fayu.connection.domain.po.Message;
import com.fayu.connection.enums.EventType;
import com.fayu.connection.enums.MessageContentType;
import com.fayu.connection.enums.OutboxStatusEnum;
import com.fayu.connection.executor.ChatBusinessExecutor;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.SingleSession;
import com.fayu.connection.utils.MessageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.fayu.common.constant.MessageEventKey.MESSAGE_CREATE_EVENT_KEY;
import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ChatMessageHandler extends io.netty.channel.SimpleChannelInboundHandler<Message<?>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MsgClient msgClient;
    private final ChatBusinessExecutor chatBusinessExecutor;
    private final MessageUtil messageUtil;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message<?> msg) {
        SingleSession session = SingleSessionFactory.getSession();
        Long userId = (Long) session.getAttr(ctx.channel(), "userId");
        String deviceId = session.getDeviceId(ctx.channel());
        if (userId == null || deviceId == null) {
            sendError(ctx, msg == null ? null : msg.getClientMessageId(), "连接尚未完成身份绑定");
            return;
        }
        if (msg == null || msg.getType() == null) {
            sendError(ctx, null, "消息格式错误");
            return;
        }

        msg.setSenderId(userId);
        msg.setDeviceId(deviceId);
        String type = msg.getType().toUpperCase();
        chatBusinessExecutor.execute(() -> {
            try {
                switch (type) {
                    case "SYNC_REQUEST" -> handleSyncRequest(ctx, userId, deviceId, msg);
                    case "DELIVERY_ACK" -> handleDeliveryAck(ctx, userId, deviceId, msg);
                    case "READ_ACK" -> handleReadAck(ctx, userId, msg);
                    default -> handleChat(ctx, userId, msg);
                }
            } catch (Exception e) {
                log.error("消息处理失败 userId={}, deviceId={}, type={}", userId, deviceId, type, e);
                sendError(ctx, msg.getClientMessageId(), e.getMessage());
            }
        });
    }

    private void handleChat(ChannelHandlerContext ctx, Long userId, Message<?> msg) {
        Long toUserId = msg.getTo();
        if (toUserId == null || msg.getClientMessageId() == null || msg.getClientMessageId().isBlank()) {
            throw new BaseException("聊天消息参数错误", 400);
        }
        Boolean friend = stringRedisTemplate.opsForSet()
                .isMember("user:friend:" + userId, String.valueOf(toUserId));
        if (!Boolean.TRUE.equals(friend)) {
            throw new BaseException("对方不是好友或已被拉黑", 403);
        }

        EventEnvelope<?> eventEnvelope = saveMsgGetEventEnvelope(msg, MessageContentType.TEXT);
        sendSavedAck(ctx, msg.getClientMessageId(), eventEnvelope.getMetadata());
        if (eventEnvelope.getMetadata().isReuseMessage()) {
            return;
        }
        publish(eventEnvelope);
        publishSenderSelfSync(eventEnvelope);
    }

    private void handleSyncRequest(ChannelHandlerContext ctx, Long userId, String deviceId, Message<?> msg) {
        SyncRequestDTO request = castPayload(msg.getPayload(), SyncRequestDTO.class);
        request.setDeviceId(deviceId);
        SyncBatchVO batch = msgClient.getSyncMessages(
                userId,
                deviceId,
                request.getConversationId(),
                request.getAfterSeq() == null ? 0L : request.getAfterSeq(),
                request.getLimit() == null ? 50 : request.getLimit());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "SYNC_BATCH");
        response.put("requestId", request.getRequestId());
        response.put("conversationId", batch.getConversationId());
        response.put("nextSeq", batch.getNextSeq());
        response.put("hasMore", batch.isHasMore());
        response.put("messages", batch.getMessages());
        ctx.channel().writeAndFlush(new TextWebSocketFrame(messageUtil.toJson(response)));
    }

    private void handleDeliveryAck(ChannelHandlerContext ctx, Long userId, String deviceId, Message<?> msg) {
        DeliveryAckDTO ack = castPayload(msg.getPayload(), DeliveryAckDTO.class);
        ack.setDeviceId(deviceId);
        msgClient.ackDelivery(userId, ack);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "DELIVERY_ACK");
        response.put("conversationId", ack.getConversationId());
        response.put("ackSeq", ack.getAckSeq());
        ctx.channel().writeAndFlush(new TextWebSocketFrame(messageUtil.toJson(response)));
    }

    private void handleReadAck(ChannelHandlerContext ctx, Long userId, Message<?> msg) {
        ReadAckDTO ack = castPayload(msg.getPayload(), ReadAckDTO.class);
        ack.setDeviceId(SingleSessionFactory.getSession().getDeviceId(ctx.channel()));
        msgClient.ackRead(userId, ack);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "READ_ACK");
        response.put("conversationId", ack.getConversationId());
        response.put("readSeq", ack.getReadSeq());
        ctx.channel().writeAndFlush(new TextWebSocketFrame(messageUtil.toJson(response)));
    }

    private void publish(EventEnvelope<?> eventEnvelope) {
        log.info("发布消息事件: {}", eventEnvelope);
        MessageMetadata metadata = eventEnvelope.getMetadata();
        String eventKey = MESSAGE_CREATE_EVENT_KEY.formatted(metadata.getMessageId());
        CorrelationData correlationData = new CorrelationData(eventKey);
        try {
            rabbitTemplate.convertAndSend(
                    MESSAGE_EXCHANGE, KEY_SEND_MESSAGE, eventEnvelope, correlationData);
            correlationData.getFuture().whenComplete((confirm, throwable) -> {
                if (throwable != null || confirm == null || !confirm.isAck()) {
                    String reason = throwable == null
                            ? (confirm == null ? "未收到发布确认" : confirm.getReason())
                            : throwable.getMessage();
                    log.warn("聊天消息发布失败 messageId={}, sendId={}, reason={}", metadata.getMessageId(), metadata.getSendId(), reason);
                    chatBusinessExecutor.execute(() -> updateStatusToRetry(metadata, reason));
                    return;
                }
                log.info("聊天消息发布成功 messageId={}, sendId={}", metadata.getMessageId(), metadata.getSendId());
                chatBusinessExecutor.execute(() -> updateStatusToSent(metadata));
            });
        } catch (Exception e) {
            log.warn("聊天消息发布失败 messageId={}, sendId={}",
                    metadata.getMessageId(), metadata.getSendId(), e);
            updateStatusToRetry(metadata, e.getMessage());
        }
    }

    private void publishSenderSelfSync(EventEnvelope<?> eventEnvelope) {
        try {
            rabbitTemplate.convertAndSend(
                    MESSAGE_EXCHANGE, KEY_SENDER_SELF_SYNC, eventEnvelope);
        } catch (Exception e) {
            MessageMetadata metadata = eventEnvelope.getMetadata();
            log.warn("发送者自同步事件发布失败 messageId={}, sendId={}",
                    metadata.getMessageId(), metadata.getSendId(), e);
        }
    }

    private EventEnvelope<?> saveMsgGetEventEnvelope(Message<?> msg, MessageContentType contentType) {
        if (msg == null || msg.getPayload() == null) {
            throw new BaseException("消息为空", 400);
        }
        String content = getContentByType(msg.getPayload(), contentType);
        SaveMsgDTO saveMsgDTO = SaveMsgDTO.builder()
                .senderId(msg.getSenderId())
                .deviceId(msg.getDeviceId())
                .toId(msg.getTo())
                .clientMessageId(msg.getClientMessageId())
                .messageType(contentType.getValue())
                .textContent(content)
                .build();
        SaveMsgVO saveMsgVO = msgClient.saveMsg(saveMsgDTO);
        if (saveMsgVO == null) {
            throw new BaseException("消息保存失败", 500);
        }
        MessageMetadata metadata = MessageMetadata.builder()
                .conversationId(saveMsgVO.getConversationId())
                .sendId(msg.getSenderId())
                .toId(msg.getTo())
                .messageId(saveMsgVO.getMessageId())
                .messageType(contentType.getValue())
                .clientMessageId(msg.getClientMessageId())
                .eventType(EventType.MESSAGE_CREATE.toString())
                .seq(saveMsgVO.getSeq())
                .timestamp(saveMsgVO.getTimeAt())
                .deviceId(msg.getDeviceId())
                .isReuseMessage(Boolean.TRUE.equals(saveMsgVO.getReuseMessage()))
                .build();
        return EventEnvelope.builder()
                .metadata(metadata)
                .payload(msg.getPayload())
                .build();
    }

    private void sendSavedAck(ChannelHandlerContext ctx, String clientMessageId, MessageMetadata metadata) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "SAVED_ACK");
        response.put("status", "SAVED");
        response.put("clientMessageId", clientMessageId);
        response.put("messageId", metadata.getMessageId());
        response.put("conversationId", metadata.getConversationId());
        response.put("seq", metadata.getSeq());
        response.put("serverTime", System.currentTimeMillis());
        ctx.channel().writeAndFlush(new TextWebSocketFrame(messageUtil.toJson(response)));
    }

    private void updateStatusToSent(MessageMetadata metadata) {
        OutBoxUpdateStatusDTO build = OutBoxUpdateStatusDTO.builder()
                .eventType(metadata.getEventType())
                .eventKey(MESSAGE_CREATE_EVENT_KEY.formatted(metadata.getMessageId()))
                .status(OutboxStatusEnum.SENT.getCode())
                .publishedAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend(
                MESSAGE_EXCHANGE, KEY_UPDATE_STATUS, build);
    }

    private void updateStatusToRetry(MessageMetadata metadata, String reason) {
        OutBoxUpdateStatusDTO statusDTO = OutBoxUpdateStatusDTO.builder()
                .eventType(metadata.getEventType())
                .eventKey(MESSAGE_CREATE_EVENT_KEY.formatted(metadata.getMessageId()))
                .lastError(reason)
                .build();
        rabbitTemplate.convertAndSend(
                MESSAGE_EXCHANGE, KEY_UPDATE_STATUS_TO_RETRY, statusDTO);
    }

    private void sendError(ChannelHandlerContext ctx, String clientMessageId, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "SEND_ERROR");
        response.put("clientMessageId", clientMessageId);
        response.put("message", message == null ? "消息处理失败" : message);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(messageUtil.toJson(response)));
    }

    private String getContentByType(Object payload, MessageContentType contentType) {
        if (contentType == MessageContentType.TEXT) {
            return castPayload(payload, ChatRequestMessageDTO.class).getContent();
        }
        return null;
    }

    private <T> T castPayload(Object payload, Class<T> type) {
        return MAPPER.convertValue(payload, type);
    }
}