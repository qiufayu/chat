package com.fayu.connection.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fayu.api.client.MsgClient;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.dto.SaveMsgAndUpdateConversationDTO;
import com.fayu.common.domain.po.EventEnvelope;
import com.fayu.common.domain.po.MessageMetadata;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.connection.enums.EventType;
import com.fayu.connection.enums.MessageContentType;
import com.fayu.connection.enums.OutboxStatusEnum;
import com.fayu.connection.executor.OutboxRetryExecutor;
import com.fayu.connection.utils.MessageUtil;
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

    private final OutboxRetryExecutor outboxRetryExecutor;
    private final MsgClient msgClient;
    private final RabbitTemplate rabbitTemplate;
    private final MessageUtil messageUtil;

    // @Scheduled(fixedDelayString = "${chat.msg.retry.scan-interval:5000}")
    public void retryScheduleTask() {
        List<MessageOutbox> dispatchList = msgClient.getRetryLists();
        if (dispatchList.isEmpty()) {
            return;
        }
        log.info("开始扫描 Outbox，获取到{}条待发布事件", dispatchList.size());
        for (MessageOutbox outbox : dispatchList) {
            if (outbox == null) {
                continue;
            }
            outboxRetryExecutor.execute(() -> publishOne(outbox));
        }
    }

    private void publishOne(MessageOutbox outbox) {
        String lockedBy = Thread.currentThread().getName();
        if (!msgClient.getRetryLock(outbox.getId(), lockedBy)) {
            return;
        }
        String eventKey = outbox.getEventKey();
        try {
            ChatResponseMessageVO<ChatRequestMessageDTO> messageInfo =
                    msgClient.getMessageInfo(outbox.getConversationId(), outbox.getSeq());
            if (messageInfo == null) {
                log.warn("消息不存在，重新插入消息，OutboxId: {}", outbox.getId());
                // 重新插入消息
                retryInsertMessageAndUpdateConversation(outbox);
                updateStatusToRetry(outbox, "消息不存在");
                return;
            }

            MessageMetadata metadata = MessageMetadata.builder()
                    .conversationId(messageInfo.getConversationId())
                    .sendId(messageInfo.getSendId())
                    .toId(messageInfo.getToId())
                    .messageId(messageInfo.getMessageId())
                    .messageType(messageInfo.getMessageType())
                    .eventType(outbox.getEventType() == null
                            ? EventType.MESSAGE_CREATE.toString() : outbox.getEventType())
                    .seq(messageInfo.getSeq())
                    .timestamp(System.currentTimeMillis())
                    .build();
            EventEnvelope<?> event = EventEnvelope.builder()
                    .metadata(metadata)
                    .payload(messageInfo.getPayload())
                    .build();

            CorrelationData correlationData = new CorrelationData(eventKey);
            rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE, KEY_SEND_MESSAGE, event, correlationData);
            correlationData.getFuture().whenComplete((confirm, throwable) -> {
                try {
                    if (throwable != null || confirm == null || !confirm.isAck()) {
                        String reason = throwable == null
                                ? (confirm == null ? "未收到发布确认" : confirm.getReason())
                                : throwable.getMessage();
                        updateStatusToRetry(outbox, reason);
                    } else {
                        msgClient.updateStatus(OutBoxUpdateStatusDTO.builder()
                                .eventType(outbox.getEventType())
                                .eventKey(eventKey)
                                .status(OutboxStatusEnum.SENT.getCode())
                                .publishedAt(LocalDateTime.now())
                                .build());
                    }
                } finally {
                    msgClient.unlockRetry(outbox.getId(), lockedBy);
                }
            });
        } catch (Exception e) {
            log.error("Outbox 事件发布异常，outboxId={}", outbox.getId(), e);
            updateStatusToRetry(outbox, e.getMessage());
            msgClient.unlockRetry(outbox.getId(), lockedBy);
        }
    }
    private void retryInsertMessageAndUpdateConversation(MessageOutbox outbox) {
        String eventKey = outbox.getEventKey();
        String[] strings = eventKey.split(":");

        if (strings.length != 4) {
            return;
        }

        Long sendId = Long.parseLong(strings[1]);
        String clientMessageId = strings[2];
        JsonNode payload = messageUtil.toJsonNode(outbox.getPayload());

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
        msgClient.saveMsgAndUpdateConversation(dto);
    }

    private void updateStatusToRetry(MessageOutbox outbox, String reason) {
        msgClient.updateStatusToRetry(OutBoxUpdateStatusDTO.builder()
                .eventType(outbox.getEventType())
                .eventKey(outbox.getEventKey())
                .lastError(reason)
                .build());
    }
}