package com.fayu.message.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdvanceDeviceCursorDTO {
    private Long userId;
    private String deviceId;
    private Long conversationId;
    private Long seq;
}
