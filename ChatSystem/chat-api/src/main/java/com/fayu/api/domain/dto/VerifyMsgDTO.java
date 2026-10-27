package com.fayu.api.domain.dto;

import lombok.Data;

@Data
public class VerifyMsgDTO {
    private Long toId;
    private Long senderId;
}
