package com.fayu.connection.domain.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;
    private Long senderId;
    private Long to;
    private String clientMessageId;
    private Long timeAt;
    private T payload;
}
