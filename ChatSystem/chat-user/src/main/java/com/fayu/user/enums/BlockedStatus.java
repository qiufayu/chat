package com.fayu.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum BlockedStatus {
    FROZEN(0, "正常"),
    NORMAL(1, "拉黑"),
    ;
    @EnumValue
    int value;
    String desc;

    BlockedStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static BlockedStatus of(int value) {
        if (value == 0) {
            return FROZEN;
        }
        if (value == 1) {
            return NORMAL;
        }

        throw new BadRequestException("账户状态错误");
    }
}

