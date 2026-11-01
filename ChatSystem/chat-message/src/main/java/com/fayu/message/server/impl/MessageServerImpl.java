package com.fayu.message.server.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.message.config.MessageRetryProperties;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.message.domain.dto.MessagePayloadDTO;
import com.fayu.common.domain.dto.SaveMsgDTO;
import com.fayu.message.domain.dto.UpdateConversationDTO;
import com.fayu.message.domain.po.Message;
import com.fayu.message.domain.po.MessageInfoRow;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.vo.ConversationVO;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.message.enums.EventType;
import com.fayu.message.enums.OutboxStatusEnum;
import com.fayu.message.enums.YesNoStatus;
import com.fayu.message.mapper.MessageMapper;
import com.fayu.message.mapper.DeviceSyncCursorMapper;
import com.fayu.message.server.IConversationServer;
import com.fayu.message.server.IMessageOutboxServer;
import com.fayu.message.server.IMessageServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.fayu.common.constant.MessageEventKey.MESSAGE_CREATE_EVENT_KEY;
import static com.fayu.common.constant.RedisConstant.SEQ_INC;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServerImpl extends ServiceImpl<MessageMapper, Message> implements IMessageServer {

    private final IConversationServer conversationServer;
    private final RedisIdWorker redisIdWorker;
    private final MessageRetryProperties messageRetryProperties;
    private final IMessageOutboxServer messageOutboxServer;
    private final MessageMapper messageMapper;
    private final DeviceSyncCursorMapper deviceSyncCursorMapper;

    @Override
    @Retryable(
            retryFor = {
                    DeadlockLoserDataAccessException.class,
                    DuplicateKeyException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 30,maxDelay = 300,multiplier = 2)
    )
    @Transactional(rollbackFor = Exception.class)
    public SaveMsgVO saveMsg(SaveMsgDTO saveMsgDTO) {
//        Long userId = UserContext.getUser();
//        if (userId == null || userId.equals(saveMsgDTO.getSenderId())){
//            throw new BaseException("业务错误",500);
//        }

        Long toId = saveMsgDTO.getToId();
        Long senderId = saveMsgDTO.getSenderId();
        String clientMessageId = saveMsgDTO.getClientMessageId();
        // 查询消息是否存在
        Message message = lambdaQuery().eq(Message::getSenderId, senderId)
                .eq(Message::getClientMessageId, clientMessageId)
                .one();
        long timeAt;
        if (message != null){
            timeAt = message.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            log.debug("重复消息 messageId = {}",message.getId());
            // 客户端使用相同 clientMessageId 重试时，当前设备已经知道这条消息已保存，
            // 不应因为重试请求而再次从同步接口拉取自己的消息。
            if (saveMsgDTO.getDeviceId() != null && !saveMsgDTO.getDeviceId().isBlank()) {
                deviceSyncCursorMapper.advance(senderId, saveMsgDTO.getDeviceId(),
                        message.getConversationId(), message.getSeq());
            }
            return SaveMsgVO.builder()
                    .reuseMessage(true)
                    .messageId(message.getId())
                    .seq(message.getSeq())
                    .conversationId(message.getConversationId())
                    .timeAt(timeAt)
                    .build();
        }

        LocalDateTime now = null;
        ConversationVO conversation = conversationServer.getConversation(toId, senderId, null);
        if (conversation == null){
            throw new BaseException("获取会话失败",500);
        }
        log.debug("toId={} -> sendId={} 获取到会话conversationId={}",toId,senderId,conversation.getConversationId());

        Long conversationId = conversation.getConversationId();
        Long seq = redisIdWorker.msgSeqIncrease(SEQ_INC.formatted(conversationId));
        now = LocalDateTime.now();
        message = Message.builder()
                .clientMessageId(clientMessageId)
                .conversationId(conversationId)
                .seq(seq)
                .senderId(saveMsgDTO.getSenderId())
                .messageType(saveMsgDTO.getMessageType())
                .textContent(saveMsgDTO.getTextContent())
                .attachmentId(saveMsgDTO.getAttachmentId())
                .isBlocked(YesNoStatus.NO.getValue())
                .recalledAt(null)
                .isRecalled(YesNoStatus.NO.getValue())
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            save(message);
        } catch (DuplicateKeyException e) {
            log.warn("消息已存在 sendId={},client_message_id={}",senderId,clientMessageId);
            throw e;
        }
        MessagePayloadDTO payloadDTO = BeanUtil.copyProperties(message, MessagePayloadDTO.class);
        String jsonPayload = JSON.toJSONString(payloadDTO);

        UpdateConversationDTO conversationDTO = UpdateConversationDTO.builder()
                .conversationId(conversationId)
                .oldLastSeq(conversation.getLastSeq())
                .newLastSeq(seq)
                .lastMessageId(message.getId())
                .lastMessageAt(now)
                .build();

        conversationServer.updateConversation(conversationDTO);

        MessageOutbox outbox = MessageOutbox.builder()
                .messageId(message.getId())
                .conversationId(conversationId)
                .seq(seq)
                .eventType(EventType.MESSAGE_CREATE.toString())
                .eventKey(MESSAGE_CREATE_EVENT_KEY.formatted(message.getId()))
                .status(OutboxStatusEnum.PENDING.getCode())
                .retryCount(0)
                .payload(jsonPayload)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            messageOutboxServer.save(outbox);
        } catch (DuplicateKeyException e) {
            log.warn("消息合子已存在消息 messageId={}",message.getId());
        }
        if (saveMsgDTO.getDeviceId() != null && !saveMsgDTO.getDeviceId().isBlank()) {
            deviceSyncCursorMapper.advance(senderId, saveMsgDTO.getDeviceId(), conversationId, seq);
        }
        return SaveMsgVO.builder()
                .messageId(message.getId())
                .reuseMessage(false)
                .seq(message.getSeq())
                .conversationId(message.getConversationId())
                .timeAt(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }

    @Override
    public  ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(Long conversationId, Long seq) {
        MessageInfoRow row = messageMapper.getMessageInfo(conversationId,seq);
        if (row == null) return null;

        ChatResponseMessageVO<ChatRequestMessageDTO> vo = new ChatResponseMessageVO<>();
        vo.setMessageId(row.getMessageId());
        vo.setConversationId(row.getConversationId());
        vo.setSeq(row.getSeq());
        vo.setConversationType(row.getConversationType());
        vo.setSendId(row.getSendId());
        vo.setToId(row.getToId());
        vo.setGroupId(row.getGroupId());
        vo.setMessageType(row.getMessageType());

        // payload 统一是 ChatRequestMessageDTO，content 取文本内容
        ChatRequestMessageDTO payload = new ChatRequestMessageDTO();
        payload.setContent(row.getTextContent());   // 图片/文件可放 attachmentId 或 url
        vo.setPayload(payload);

        // 撤回 / 违规
        if (row.getIsRecalled() != null && row.getIsRecalled() == 1) {
            vo.setErrorContent("消息已撤回");
        }
        if (row.getIsBlocked() != null && row.getIsBlocked() == 1) {
            vo.setErrorContent("消息含违规内容");
        }
        return vo;
    }
}
