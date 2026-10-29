package com.fayu.message.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OutBoxUpdateStatusDTO {
    private String eventType;
    private String eventKey;
    private String lastError;
    private Integer status;
    private LocalDateTime lockedAt;
    private LocalDateTime publishedAt;
}
