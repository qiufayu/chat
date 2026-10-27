package com.fayu.message.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 会话状态：0已关闭，1正常，2封禁
 */
@Getter
public enum ConversationStatus {
    CLOSED(0, "已关闭"),
    NORMAL(1, "正常"),
    BANNED(2, "封禁"),
    ;
    @EnumValue
    int value;
    String desc;

    ConversationStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static ConversationStatus of(int value) {
        if (value == 0) {
            return CLOSED;
        }
        if (value == 1) {
            return NORMAL;
        }
        if (value == 2) {
            return BANNED;
        }
        throw new BadRequestException("会话状态错误");
    }
}