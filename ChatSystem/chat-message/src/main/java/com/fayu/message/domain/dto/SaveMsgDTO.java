package com.fayu.message.domain.dto;

import lombok.Data;

@Data
public class SaveMsgDTO {
    private Long senderId;
    private Long toId;
    private String clientMessageId;
    private Integer messageType;
    private String textContent;
    private Long attachmentId;
}
