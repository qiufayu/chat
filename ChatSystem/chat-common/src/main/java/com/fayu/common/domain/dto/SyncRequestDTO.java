package com.fayu.common.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 请求同步某个会话中设备尚未确认接收的消息。
 */
@Data
public class SyncRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deviceId;
    private Long conversationId;
    private Long afterSeq;
    private Integer limit;
    private String requestId;
}
