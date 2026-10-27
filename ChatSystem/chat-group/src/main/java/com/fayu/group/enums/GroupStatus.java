package com.fayu.group.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum GroupStatus {
    CLOSE(0, "解散"),
    NORMAL(1, "正常"),
    BAN(2,"封禁"),
    ;
    @EnumValue
    int value;
    String desc;

    GroupStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static GroupStatus of(int value) {
        if (value == 0) {
            return CLOSE;
        }
        if (value == 1) {
            return NORMAL;
        }
        if (value == 2){
            return BAN;
        }
        throw new BadRequestException("账户状态错误");
    }

}
