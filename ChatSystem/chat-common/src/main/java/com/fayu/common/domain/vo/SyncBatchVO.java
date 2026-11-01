package com.fayu.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncBatchVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long conversationId;
    private Long nextSeq;
    private boolean hasMore;
    private List<SyncMessageVO> messages;
}
