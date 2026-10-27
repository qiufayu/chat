package com.fayu.api.domain.vo;

import lombok.Data;

@Data
public class SaveMsgVO {
    private Long messageId;
    private Long seq;
    private Long conversationId;
    private Long timeAt;
}
