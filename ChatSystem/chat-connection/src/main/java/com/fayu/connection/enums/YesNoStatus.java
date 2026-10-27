package com.fayu.connection.enums;

import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 通用是否状态：0否、1是
 */
@Getter
public enum YesNoStatus {
    NO(0, "否"),
    YES(1, "是"),
    ;
    int value;
    String desc;

    YesNoStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static YesNoStatus of(int value) {
        if (value == 0) {
            return NO;
        }
        if (value == 1) {
            return YES;
        }
        throw new BadRequestException("是否状态错误");
    }
}