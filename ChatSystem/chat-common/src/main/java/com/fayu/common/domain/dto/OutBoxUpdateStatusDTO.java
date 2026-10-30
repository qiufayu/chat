package com.fayu.common.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OutBoxUpdateStatusDTO {
    private String eventType;
    private String eventKey;
    private String lastError;
    private Integer status;
    private LocalDateTime lockedAt;
    private LocalDateTime publishedAt;
}