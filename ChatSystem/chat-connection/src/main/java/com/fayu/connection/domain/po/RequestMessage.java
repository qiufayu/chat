package com.fayu.connection.domain.po;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;

@Data

public class RequestMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;
    private Long senderId;
    private Long to;
    private String clientMessageId;
    private String deviceId;
    private Long conversationId;
    private Long afterSeq;
    private Integer limit;
    private String requestId;
    private Long ackSeq;
    private Long readSeq;
    private Long timeAt;
    private JsonNode payload;
}
