package com.fayu.connection.domain.vo;

import com.fayu.connection.domain.po.RequestMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public  class ChatResponseMessageVO<T> {
    private Long messageId;
    private Long conversationId;
    private Long seq;
    private Integer conversationType;
    private Long sendId;
    private Long toId;
    private Long groupId;
    private Integer messageType;
    private T payload;
    private String errorContent;
    private String url;
}
