package com.fayu.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntervalSyncBatchVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long conversationId;
    private String deviceId;
    private List<SyncMessageVO> messages;
}
