package com.fayu.connection.enums;


import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 会话类型：1单聊，2群聊
 */
@Getter
public enum ConversationType {
    SINGLE(1, "单聊"),
    GROUP(2, "群聊"),
    ;

    int value;
    String desc;

    ConversationType(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static ConversationType of(int value) {
        if (value == 1) {
            return SINGLE;
        }
        if (value == 2) {
            return GROUP;
        }
        throw new BadRequestException("会话类型错误");
    }
}