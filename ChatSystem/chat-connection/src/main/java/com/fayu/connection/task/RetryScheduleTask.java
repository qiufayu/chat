package com.fayu.connection.task;

import com.fayu.api.client.MsgClient;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.dto.SaveOffMessageBoxDTO;
import com.fayu.connection.enums.OffMsgStatus;
import com.fayu.connection.enums.OutboxStatusEnum;
import com.fayu.connection.executor.OutboxRetryExecutor;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.SingleSession;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.fayu.common.constant.RabbitmqConstant.KEY_SAVE_OFF_MESSAGE;
import static com.fayu.common.constant.RabbitmqConstant.MESSAGE_EXCHANGE;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduleTask {

    private final OutboxRetryExecutor outboxRetryExecutor;
    private final MsgClient msgClient;
    private final MessageUtil messageUtil;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(cron = "*/5 * * * * ?")
    public void retryScheduleTask() {
        SingleSession session = SingleSessionFactory.getSession();
        List<MessageOutbox> retryLists = msgClient.getRetryLists();
        log.info("开始执行消息重试......");
        log.info("获取到{}条重试消息，开始执行.....",retryLists.size());
        for (MessageOutbox outbox : retryLists) {
            if (outbox == null) continue;
            String eventType = outbox.getEventType();
            String eventKey = outbox.getEventKey();
            Long messageId = outbox.getMessageId();
            Long outboxId = outbox.getId();
            outboxRetryExecutor.execute(() -> {
                Thread thread = Thread.currentThread();
                String lockedBy = thread.getName();
                boolean retryLock = msgClient.getRetryLock(outboxId, lockedBy);
                boolean unlockNeeded = true;
                if (!retryLock) return;
                try {
                    Long conversationId = outbox.getConversationId();
                    Long seq = outbox.getSeq();
                    ChatResponseMessageVO<ChatRequestMessageDTO> messageInfo = msgClient.getMessageInfo(conversationId, seq);
                    if (messageInfo == null) {
                        // 消息不存在
                        updateStatusToRetry(eventType, eventKey, new IllegalArgumentException("消息不存在"));
                        return;
                    }
                    Long toId = messageInfo.getToId();
                    Channel channel = session.getChannel(toId);
                    if (channel == null || !channel.isOpen()) {
                        log.debug("会话不存在或用户已离线，消息id={},用户id={} , 保存到离线队列",messageId,toId);
                        saveToOffBox(messageId, toId, conversationId,seq);
                        updateStatusToPending(eventType,eventKey);
                        return;
                    }
                    String json = messageUtil.toJson(messageInfo);
                    channel.writeAndFlush(new TextWebSocketFrame(json)).addListener(future -> {
                        try {
                            if (future.isSuccess()){
                                updateStatusToPass(eventType, eventKey);
                            }else {
                                updateStatusToRetry(eventType, eventKey, future.cause());
                                log.error("重试处理异常 outboxId={}", outboxId, future.cause());
                            }
                        } finally {
                            msgClient.unlockRetry(outboxId, lockedBy);
                        }
                    });
                    unlockNeeded = false;
                    log.debug("消息messageId={}重试成功",messageId);
                } catch (Exception e) {
                    log.error("重试处理异常 outboxId={}", outboxId, e);
                    updateStatusToRetry(eventType, eventKey, e);
                } finally {
                    if (unlockNeeded) {
                        msgClient.unlockRetry(outboxId, lockedBy);   // 只兜底同步异常
                    }
                }
            });
        }
    }

    private void saveToOffBox(Long messageId, Long toId, Long conversationId, Long seq) {
        SaveOffMessageBoxDTO offMessageBoxDTO = SaveOffMessageBoxDTO.builder()
                .messageId(messageId)
                .userId(toId)
                .seq(seq)
                .conversationId(conversationId)
                .createdAt(LocalDateTime.now())
                .status(OffMsgStatus.PENDING.getCode())
                .build();
        rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_SAVE_OFF_MESSAGE,offMessageBoxDTO);
    }

    private void updateStatusToPending(String eventType, String eventKey){
        OutBoxUpdateStatusDTO statusDTO = OutBoxUpdateStatusDTO.builder()
                .status(OutboxStatusEnum.PENDING.getCode())
                .eventType(eventType)
                .eventKey(eventKey)
                .publishedAt(null)
                .build();
        // 修改outbox状态
        msgClient.updateStatus(statusDTO);
    }

    private void updateStatusToPass(String eventType, String eventKey) {
        OutBoxUpdateStatusDTO statusDTO = OutBoxUpdateStatusDTO.builder()
                .status(OutboxStatusEnum.SENT.getCode()).eventType(eventType).eventKey(eventKey)
                .publishedAt(LocalDateTime.now()).build();
        // 修改outbox状态
        msgClient.updateStatus(statusDTO);
    }

    private void updateStatusToRetry(String eventType, String eventKey, Throwable lastError) {
        OutBoxUpdateStatusDTO statusDTO = OutBoxUpdateStatusDTO.builder()
                .eventType(eventType).eventKey(eventKey).lastError(lastError.getMessage())
                .publishedAt(LocalDateTime.now()).build();
        // 修改outbox状态
        msgClient.updateStatusToRetry(statusDTO);
    }
}
