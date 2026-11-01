package com.fayu.common.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaveMsgDTO {
    private Long senderId;
    private String deviceId;
    private Long toId;
    private String clientMessageId;
    private Integer messageType;
    private String textContent;
    private Long attachmentId;
}
