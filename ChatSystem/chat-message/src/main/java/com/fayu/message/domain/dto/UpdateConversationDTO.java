package com.fayu.message.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UpdateConversationDTO {
    private Long conversationId;
    private Long oldLastSeq;
    private Long newLastSeq;
    private Long lastMessageId;
    private LocalDateTime lastMessageAt;
    private Integer status;
}
