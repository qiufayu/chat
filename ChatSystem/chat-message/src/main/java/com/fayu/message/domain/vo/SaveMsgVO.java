package com.fayu.message.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaveMsgVO {
    private Long messageId;
    private Long seq;
    private Long conversationId;
    private Long timeAt;
    private Boolean reuseMessage;
}
