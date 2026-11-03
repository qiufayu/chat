package com.fayu.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveMsgVO {
    private Long messageId;
    private Long outboxId;
    private Long seq;
    private Long conversationId;
    private Long timeAt;
    private Boolean reuseMessage;
}