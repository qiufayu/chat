package com.fayu.message.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.dto.SaveMsgAndUpdateConversationDTO;
import com.fayu.common.domain.po.EventEnvelope;
import com.fayu.common.domain.po.MessageMetadata;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.enums.EventType;
import com.fayu.common.enums.OutboxStatusEnum;

import com.fayu.message.executor.OutboxRetryExecutor;
import com.fayu.message.server.IMessageOutboxServer;
import com.fayu.message.server.IMessageServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.fayu.common.constant.RabbitmqConstant.KEY_SEND_MESSAGE;
import static com.fayu.common.constant.RabbitmqConstant.MESSAGE_EXCHANGE;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduleTask {

    private final OutboxRetryExecutor OutboxRetryExecutor;
    private final IMessageOutboxServer messageOutboxServer;
    private final IMessageServer messageServer;
    private final RabbitTemplate rabbitTemplate;


    @Scheduled(fixedDelayString = "${chat.msg.retry.scan-interval:5000}")
    public void retryScheduleTask() {
        List<MessageOutbox> dispatchList = messageOutboxServer.getRetryLists();
        if (dispatchList.isEmpty()) {
            return;
        }
        log.info("开始扫描 Outbox，获取到{}条待发布事件", dispatchList.size());
        for (MessageOutbox outbox : dispatchList) {
            if (outbox == null) {
                continue;
            }
            log.debug("开始处理 Outbox，OutboxId: {}", outbox.getId());
            OutboxRetryExecutor.execute(() -> publishOne(outbox));
        }
    }

    private void publishOne(MessageOutbox outbox) {
        String lockedBy = Thread.currentThread().getName();
        if (!messageOutboxServer.getRetryLock(outbox.getId(), lockedBy)) {
            log.warn("Outbox 获取锁失败，OutboxId: {}", outbox.getId());
            return;
        }
        log.debug("Outbox 获取锁成功，OutboxId: {}", outbox.getId());
        String eventKey = outbox.getEventKey();
        try {
            ChatResponseMessageVO<ChatRequestMessageDTO> messageInfo =
                    messageServer.getMessageInfo(outbox.getConversationId(), outbox.getSeq());
            if (messageInfo == null) {
                log.warn("消息不存在，重新插入消息，OutboxId: {}", outbox.getId());
                // 重新插入消息
                retryInsertMessageAndUpdateConversation(outbox);
                messageInfo = messageServer.getMessageInfo(outbox.getConversationId(), outbox.getSeq());
                if (messageInfo == null) {
                    throw new RuntimeException("消息不存在，OutboxId: " + outbox.getId());
                }
            }
            String clientMessageId = getClientMessageId(eventKey);
            EventEnvelope<?> event = getEventEnvelope(outbox, messageInfo, clientMessageId);

            CorrelationData correlationData = new CorrelationData(eventKey);
            rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE, KEY_SEND_MESSAGE, event, correlationData);
            correlationData.getFuture().whenComplete((confirm, throwable) -> {
                try {
                    if (throwable != null || confirm == null || !confirm.isAck()) {
                        log.error("Outbox 事件发布失败，OutboxId: {}", outbox.getId(), throwable);
                        String reason = throwable == null
                                ? (confirm == null ? "未收到发布确认" : confirm.getReason())
                                : throwable.getMessage();
                        updateStatusToRetry(outbox, reason);
                    } else {
                        log.debug("Outbox 事件发布成功，OutboxId: {}", outbox.getId());
                        messageOutboxServer.updateStatus(OutBoxUpdateStatusDTO.builder()
                                .eventType(outbox.getEventType())
                                .eventKey(eventKey)
                                .status(OutboxStatusEnum.SENT.getCode())
                                .publishedAt(LocalDateTime.now())
                                .build());
                    }
                } finally {
                    messageOutboxServer.unlockRetry(outbox.getId(), lockedBy);
                }
            });
        } catch (Exception e) {
            log.error("Outbox 事件发布异常，outboxId={}", outbox.getId(), e);
            updateStatusToRetry(outbox, e.getMessage());
            messageOutboxServer.unlockRetry(outbox.getId(), lockedBy);
        }
    }

    private static EventEnvelope<?> getEventEnvelope(MessageOutbox outbox, ChatResponseMessageVO<ChatRequestMessageDTO> messageInfo, String clientMessageId) {
        MessageMetadata metadata = MessageMetadata.builder()
                .conversationId(messageInfo.getConversationId())
                .clientMessageId(clientMessageId)
                .sendId(messageInfo.getSendId())
                .toId(messageInfo.getToId())
                .messageId(messageInfo.getMessageId())
                .messageType(messageInfo.getMessageType())
                .eventType(outbox.getEventType() == null ? EventType.MESSAGE_CREATE.toString() : outbox.getEventType())
                .seq(messageInfo.getSeq())
                .timestamp(System.currentTimeMillis())
                .build();
        return EventEnvelope.builder()
                .metadata(metadata)
                .payload(messageInfo.getPayload())
                .build();
    }

    private void retryInsertMessageAndUpdateConversation(MessageOutbox outbox) {
        String eventKey = outbox.getEventKey();
        String[] strings = eventKey.split(":");

        if (strings.length != 4) {
            return;
        }

        Long sendId = Long.parseLong(strings[1]);
        String clientMessageId = strings[2];
        ObjectMapper mapper = new ObjectMapper();
        JsonNode payload = null;
        try {
            payload = mapper.readTree(outbox.getPayload());
        } catch (JsonProcessingException e) {
            log.warn("解析消息失败，OutboxId: {}", outbox.getId(), e);
            throw new RuntimeException(e);
        }

        Integer messageType = payload.get("messageType").intValue();

        SaveMsgAndUpdateConversationDTO dto = SaveMsgAndUpdateConversationDTO.builder()
                .clientMessageId(clientMessageId)
                .sendId(sendId)
                .seq(outbox.getSeq())
                .conversationId(outbox.getConversationId())
                .messageId(outbox.getMessageId())
                .payload(payload)
                .messageType(messageType)
                .eventType(outbox.getEventType())
                .build();
        messageServer.saveMsgAndUpdateConversation(dto);

    }

    private void updateStatusToRetry(MessageOutbox outbox, String reason) {
        messageOutboxServer.updateStatusToRetry(OutBoxUpdateStatusDTO.builder()
                .eventType(outbox.getEventType())
                .eventKey(outbox.getEventKey())
                .lastError(reason)
                .build());
    }
    private String getClientMessageId(String eventKey) {
        String[] strings = eventKey.split(":");
        if (strings.length != 4) {
            return null;
        }
        return strings[2];
    }
}