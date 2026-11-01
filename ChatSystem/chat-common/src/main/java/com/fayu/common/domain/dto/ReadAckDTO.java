package com.fayu.common.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户实际阅读位置确认。
 */
@Data
public class ReadAckDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deviceId;
    private Long conversationId;
    private Long readSeq;
}
