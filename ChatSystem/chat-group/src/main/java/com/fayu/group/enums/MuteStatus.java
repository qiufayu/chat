package com.fayu.group.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum MuteStatus {
    FALSE(0, "否"),
    TRUE(1, "是"),
    ;
    @EnumValue
    int value;
    String desc;

    MuteStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static MuteStatus of(int value) {
        if (value == 0) {
            return FALSE;
        }
        if (value == 1) {
            return TRUE;
        }
        throw new BadRequestException("账户状态错误");
    }
}
