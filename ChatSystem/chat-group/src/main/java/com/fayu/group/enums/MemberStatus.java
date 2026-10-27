package com.fayu.group.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum MemberStatus {
    EXIT(0, "退出"),
    NORMAL(1, "正常"),
    KICK(2,"被踢"),
    ;
    @EnumValue
    int value;
    String desc;

    MemberStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static MemberStatus of(int value) {
        if (value == 0) {
            return EXIT;
        }
        if (value == 1) {
            return NORMAL;
        }
        if (value == 2){
            return KICK;
        }
        throw new BadRequestException("账户状态错误");
    }
}