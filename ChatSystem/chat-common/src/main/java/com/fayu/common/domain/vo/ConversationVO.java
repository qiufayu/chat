package com.fayu.common.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private Long conversationId;

    private Integer conversationType;

    private Long userLowId;

    private Long userHighId;

    private Long groupId;

    private Long lastSeq;

    private Long lastMessageId;

    private LocalDateTime lastMessageAt;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}