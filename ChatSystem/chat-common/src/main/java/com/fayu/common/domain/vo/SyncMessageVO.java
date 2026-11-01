package com.fayu.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncMessageVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long messageId;
    private Long conversationId;
    private Long seq;
    private Long senderId;
    private Integer messageType;
    private String textContent;
    private Long attachmentId;
    private Integer isRecalled;
    private Integer isBlocked;
    private LocalDateTime createdAt;
}
