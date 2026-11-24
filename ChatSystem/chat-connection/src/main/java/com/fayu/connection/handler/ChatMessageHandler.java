package com.fayu.connection.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fayu.api.client.MsgClient;
import com.fayu.api.client.UserClient;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.common.domain.dto.*;
import com.fayu.common.domain.po.EventEnvelope;
import com.fayu.common.domain.po.MessageMetadata;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.common.domain.vo.SyncBatchVO;
import com.fayu.common.exception.BaseException;
import com.fayu.connection.domain.po.Message;
import com.fayu.common.enums.EventType;
import com.fayu.connection.enums.MessageContentType;
import com.fayu.common.enums.OutboxStatusEnum;
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
    private final UserClient userClient;
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

        boolean timing = sampleTiming(msg.getClientMessageId());
        long submittedAt = System.nanoTime();
        chatBusinessExecutor.execute(() -> {
            long startedAt = System.nanoTime();
            double queueMs = (startedAt - submittedAt) / 1_000_000.0;
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
            }finally {
                if (timing) {
                    log.info(
                            "[PERF_QUEUE] clientMessageId={} queueMs={} taskMs={} active={} poolSize={} queueSize={}",
                            msg.getClientMessageId(),
                            queueMs,
                            elapsedMs(startedAt),
                            chatBusinessExecutor.getActiveCount(),
                            chatBusinessExecutor.getPoolSize(),
                            chatBusinessExecutor.getThreadPoolExecutor()
                                    .getQueue().size()
                    );
                }
            }
        });
    }

    private void handleChat(ChannelHandlerContext ctx, Long userId, Message<?> msg) {
        Long toUserId = msg.getTo();
        if (toUserId == null || msg.getClientMessageId() == null || msg.getClientMessageId().isBlank()) {
            throw new BaseException("聊天消息参数错误", 400);
        }
        VerifyMsgDTO verify = new VerifyMsgDTO();
        verify.setSenderId(userId);
        verify.setToId(toUserId);
        // Executor threads do not inherit UserContext; forward the bound channel identity explicitly.
        if (!userClient.verifyFriend(verify, userId)) {
            throw new BaseException("对方不是好友或已被拉黑", 403);
        }
        ChatRequestMessageDTO request = castPayload(msg.getPayload(), ChatRequestMessageDTO.class);
        if (request.getMessageType() == null) {
            throw new BaseException("messageType 不能为空", 400);
        }
        MessageContentType messageType = MessageContentType.of(request.getMessageType());
        EventEnvelope<?> eventEnvelope = saveMsgGetEventEnvelope(msg, messageType);
        MessageMetadata metadata = eventEnvelope.getMetadata();
        sendSavedAck(ctx, msg.getClientMessageId(), metadata);
        if (metadata.isReuseMessage()) {
            return;
        }
        // 异步插入消息和更新会话
        chatBusinessExecutor.execute(()->{
            try {
                String lockedBy = Thread.currentThread().getName();
                if (!msgClient.getRetryLock(metadata.getOutboxId(), lockedBy)) {
                    return;
                }
                try {
                    insertMessageAndUpdateConversation(eventEnvelope);
                    // 发布方法内部监听MQ确认，负责后续状态处理
                    publish(eventEnvelope);
                    // 发送者其他设备的自同步，当前保持独立处理
                    publishSenderSelfSync(eventEnvelope);
                } catch (Exception e) {
                    log.error("消息后台处理失败，messageId={}", metadata.getMessageId(), e);
                    finishOutboxAsync(metadata, false, e.getMessage());
                }
            } catch (Exception e) {
                log.error("消息后台处理失败，messageId={}", metadata.getMessageId(), e);
                updateStatusToRetry(metadata, e.getMessage());
            }
        });
    }

    private void insertMessageAndUpdateConversation(EventEnvelope<?> eventEnvelope){
        MessageMetadata metadata = eventEnvelope.getMetadata();
        if (metadata == null) {
            return;
        }
        JsonNode payload = MAPPER.valueToTree(eventEnvelope.getPayload());
        SaveMsgAndUpdateConversationDTO dto = SaveMsgAndUpdateConversationDTO.builder()
                .conversationId(metadata.getConversationId())
                .messageId(metadata.getMessageId())
                .clientMessageId(metadata.getClientMessageId())
                .seq(metadata.getSeq())
                .messageType(metadata.getMessageType())
                .eventType(metadata.getEventType())
                .sendId(metadata.getSendId())
                .payload(payload)
                .build();
        msgClient.saveMsgAndUpdateConversation(dto);

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
        String eventKey = MESSAGE_CREATE_EVENT_KEY.formatted(metadata.getSendId(), metadata.getClientMessageId());
        CorrelationData correlationData = new CorrelationData(eventKey);
        try {
            rabbitTemplate.convertAndSend(
                    MESSAGE_EXCHANGE, KEY_SEND_MESSAGE, eventEnvelope, correlationData);
            correlationData.getFuture().whenComplete((confirm, error) -> {
                boolean success = error == null
                        && confirm != null
                        && confirm.isAck()
                        && correlationData.getReturned() == null;

                String reason = null;
                if (!success) {
                    reason = error != null ? error.getMessage() : correlationData.getReturned() != null
                            ? "消息未路由到目标队列" : confirm == null ? "未收到发布确认" : confirm.getReason();
                }

                finishOutboxAsync(metadata, success, reason);
            });
        } catch (Exception e) {
            log.warn("聊天消息发布失败 messageId={}, sendId={}",
                    metadata.getMessageId(), metadata.getSendId(), e);
            updateStatusToRetry(metadata, e.getMessage());
        }
    }

    private void finishOutboxAsync(MessageMetadata metadata, boolean success, String reason) {
        try {
            chatBusinessExecutor.execute(() -> {
                try {
                    OutBoxUpdateStatusDTO dto = OutBoxUpdateStatusDTO.builder()
                            .eventType(metadata.getEventType())
                            .eventKey(getEventKey(metadata.getSendId(), metadata.getClientMessageId()))
                            .status(success ? OutboxStatusEnum.SENT.getCode() : null)
                            .publishedAt(success ? LocalDateTime.now() : null)
                            .lastError(reason)
                            .build();
                    // sql释放锁
                    boolean updated = success
                            ? msgClient.updateStatus(dto)
                            : msgClient.updateStatusToRetry(dto);

                    if (!updated) {
                        log.warn("Outbox 状态未更新，保留锁等待恢复，outboxId={}",
                                metadata.getOutboxId());
                    }
                } catch (Exception e) {
                    // 状态更新失败时，不主动解锁。
                    // 等待锁过期后，由扫描任务恢复。
                    log.error("Outbox 状态更新异常，outboxId={}",
                            metadata.getOutboxId(), e);
                }
            });
        } catch (Exception e) {
            log.error("Outbox 状态处理任务提交失败，outboxId={}",
                    metadata.getOutboxId(), e);
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
        SaveOutboxDTO saveOutboxDTO = SaveOutboxDTO.builder()
                .senderId(msg.getSenderId())
                .deviceId(msg.getDeviceId())
                .toId(msg.getTo())
                .clientMessageId(msg.getClientMessageId())
                .messageType(contentType.getValue())
                .textContent(content)
                .build();
        SaveMsgVO saveMsgVO;
        long feignStartedAt = System.nanoTime();

        try {
            saveMsgVO = msgClient.saveOutbox(saveOutboxDTO);
        } finally {
            if (sampleTiming(saveOutboxDTO.getClientMessageId())) {
                log.info("[PERF_FEIGN] clientMessageId={} totalMs={}",
                        saveOutboxDTO.getClientMessageId(),
                        elapsedMs(feignStartedAt));
            }
        }
        if (saveMsgVO == null) {
            throw new BaseException("消息保存失败", 500);
        }
        MessageMetadata metadata = buildMetadata(msg, contentType, saveMsgVO);

        return EventEnvelope.builder()
                .metadata(metadata)
                .payload(msg.getPayload())
                .build();
    }

    private static MessageMetadata buildMetadata(Message<?> msg, MessageContentType contentType, SaveMsgVO saveMsgVO) {
        return MessageMetadata.builder()
                .conversationId(saveMsgVO.getConversationId())
                .sendId(msg.getSenderId())
                .toId(msg.getTo())
                .outboxId(saveMsgVO.getOutboxId())
                .messageId(saveMsgVO.getMessageId())
                .messageType(contentType.getValue())
                .clientMessageId(msg.getClientMessageId())
                .eventType(EventType.MESSAGE_CREATE.toString())
                .seq(saveMsgVO.getSeq())
                .timestamp(saveMsgVO.getTimeAt())
                .deviceId(msg.getDeviceId())
                .isReuseMessage(Boolean.TRUE.equals(saveMsgVO.getReuseMessage()))
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
                .eventKey(MESSAGE_CREATE_EVENT_KEY.formatted(metadata.getSendId(), metadata.getClientMessageId()))
                .status(OutboxStatusEnum.SENT.getCode())
                .publishedAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend(
                MESSAGE_EXCHANGE, KEY_UPDATE_STATUS, build);
    }

    private void updateStatusToRetry(MessageMetadata metadata, String reason) {
        OutBoxUpdateStatusDTO statusDTO = OutBoxUpdateStatusDTO.builder()
                .eventType(metadata.getEventType())
                .eventKey(getEventKey(metadata.getSendId(),metadata.getClientMessageId()))
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

    private static boolean sampleTiming(String clientMessageId) {
        return clientMessageId != null
                && Math.floorMod(clientMessageId.hashCode(), 100) == 0;
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
    private String getEventKey(Long sendId,String clientMessageId){
        return MESSAGE_CREATE_EVENT_KEY.formatted(sendId,clientMessageId);
    }
}
