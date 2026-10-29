package com.fayu.message.domain.po;

import lombok.Data;

@Data
public class MessageInfoRow {
    private Long messageId;
    private Long conversationId;
    private Long seq;
    private Integer conversationType;
    private Long sendId;
    private Long toId;
    private Long groupId;
    private Integer messageType;
    private String textContent;
    private Long attachmentId;
    private Integer isRecalled;
    private Integer isBlocked;
}