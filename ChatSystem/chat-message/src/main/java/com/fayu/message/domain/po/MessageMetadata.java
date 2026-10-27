package com.fayu.message.domain.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageMetadata {
    private Long conversationId;

    private Long sendId;
    private Long toId;
    private String groupId;

    private Long messageId;
    private String clientMessageId;
    private String eventType;

    private Long seq;
    private Long timestamp;

    private String traceId;
}
