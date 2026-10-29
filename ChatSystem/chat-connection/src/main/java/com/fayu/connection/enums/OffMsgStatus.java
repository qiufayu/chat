package com.fayu.connection.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 离线消息收件箱状态
 * 与 off_message_box.status 严格对齐：0-待拉取 1-已ACK
 */
@Getter
public enum OffMsgStatus {

    PENDING(0, "待拉取"),
    ACKED(1, "已ACK");

    /**
     * 数据库存储值
     */
    @JsonValue
    private final Integer code;

    /**
     * 描述
     */
    private final String desc;

    OffMsgStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}