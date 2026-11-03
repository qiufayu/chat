package com.fayu.common.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaveMsgAndUpdateConversationDTO {

    private Long conversationId;
    private Long messageId;
    private String clientMessageId;
    private Long seq;
    private Integer messageType;
    private String eventType;
    private Long sendId;
    private JsonNode payload;
}
