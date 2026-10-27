package com.fayu.api.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SaveMsgDTO {
    private Long senderId;
    private Long toId;
    private String clientMessageId;
    private Integer messageType;
    private String textContent;
    private Long attachmentId;
}
