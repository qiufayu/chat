package com.fayu.message.domain.vo;


import lombok.Data;

@Data
public class ConversationSeqOfDeviceVO {
    private Long conversationId;
    private String deviceId;
    private Long lastSeq;
    private Long lastSyncedSeq;
}
