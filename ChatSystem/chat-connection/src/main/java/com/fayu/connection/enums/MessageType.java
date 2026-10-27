package com.fayu.connection.enums;

import lombok.Getter;

@Getter
public enum MessageType {

    ERROR,
    ACK,
    CHAT,
    PING,
    PONG
}
