package com.fayu.message.server.impl;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.SaveMsgAndUpdateConversationDTO;
import com.fayu.common.exception.BaseException;
import com.fayu.message.domain.dto.UpdateConversationDTO;
import com.fayu.message.domain.po.Message;
import com.fayu.message.domain.po.MessageInfoRow;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.message.enums.MessageContentType;
import com.fayu.message.enums.YesNoStatus;
import com.fayu.message.mapper.MessageMapper;
import com.fayu.message.server.IConversationServer;
import com.fayu.message.server.IMessageServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServerImpl extends ServiceImpl<MessageMapper, Message> implements IMessageServer {

    private final IConversationServer conversationServer;
    private final MessageMapper messageMapper;

    @Override
    public ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(Long conversationId, Long seq) {
        MessageInfoRow row = messageMapper.getMessageInfo(conversationId, seq);
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

        ChatRequestMessageDTO payload = new ChatRequestMessageDTO();
        payload.setContent(row.getTextContent());
        vo.setPayload(payload);

        if (row.getIsRecalled() != null && row.getIsRecalled() == 1) {
            vo.setErrorContent("消息已撤回");
        }
        if (row.getIsBlocked() != null && row.getIsBlocked() == 1) {
            vo.setErrorContent("消息含违规内容");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMsgAndUpdateConversation(SaveMsgAndUpdateConversationDTO dto) {
        if (dto == null) return;

        Long messageId = dto.getMessageId();
        if (ObjUtil.isEmpty(messageId)) {
            return;
        }
        Message message = messageMapper.selectById(messageId);
        if (message != null) {
            log.warn("消息已存在 messageId={}", messageId);
            return;
        }

        JsonNode payload = dto.getPayload();
        Integer messageType = dto.getMessageType();
        JsonNode textNode = payload.get("textContent");
        if (textNode == null) {
            textNode = payload.get("content");
        }

        if (textNode == null || !textNode.isTextual()) {
            throw new BaseException("文字消息缺少有效的文本字段", 400);
        }

        String textContent = textNode.asText();
        Long attachmentId = messageType == MessageContentType.IMAGE.getValue() || messageType == MessageContentType.FILE.getValue() ? payload.get("attachmentId").asLong() : null;
        message = Message.builder()
                .id(messageId)
                .seq(dto.getSeq())
                .senderId(dto.getSendId())
                .clientMessageId(dto.getClientMessageId())
                .conversationId(dto.getConversationId())
                .messageType(messageType)
                .textContent(textContent)
                .attachmentId(attachmentId)
                .isBlocked(YesNoStatus.NO.getValue())
                .recalledAt(null)
                .isRecalled(YesNoStatus.NO.getValue())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        boolean inserted = false;
        try {
            inserted = messageMapper.insert(message) > 0;
        } catch (DuplicateKeyException e) {
            log.warn("消息已存在", e);
            return;
        }
        UpdateConversationDTO conversationDTO = UpdateConversationDTO.builder()
                .conversationId(dto.getConversationId())
                .newLastSeq(dto.getSeq())
                .lastMessageId(message.getId())
                .lastMessageAt(message.getCreatedAt())
                .build();
        conversationServer.updateConversation(conversationDTO);
    }
}