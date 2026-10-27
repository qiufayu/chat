package com.fayu.group.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum JoinType {
    FREEDOM(0, "自由"),
    CHECK(1, "审批"),
    BAN(2,"禁止"),
    ;
    @EnumValue
    int value;
    String desc;

    JoinType(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static JoinType of(int value) {
        if (value == 0) {
            return FREEDOM;
        }
        if (value == 1) {
            return CHECK;
        }
        if (value == 2){
            return BAN;
        }
        throw new BadRequestException("账户状态错误");
    }
}