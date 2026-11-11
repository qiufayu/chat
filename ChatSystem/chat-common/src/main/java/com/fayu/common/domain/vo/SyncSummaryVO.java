package com.fayu.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long conversationId;
    private Long lastSeq;
    private Long lastSyncedSeq;
    private Long pendingCount;
    private Long unreadCount;
    private String lastMessage;
}
