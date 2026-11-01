package com.fayu.common.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备已连续接收消息的确认。
 */
@Data
public class DeliveryAckDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deviceId;
    private Long conversationId;
    private Long ackSeq;
}
