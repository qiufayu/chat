package com.fayu.group.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum GroupType {
    GROUP(0, "群聊"),
    DISCUSS(1, "讨论"),
    CHANNEL(2,"频道"),
    ;
    @EnumValue
    int value;
    String desc;

    GroupType(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static GroupType of(int value) {
        if (value == 0) {
            return GROUP;
        }
        if (value == 1) {
            return DISCUSS;
        }
        if (value == 2){
            return CHANNEL;
        }
        throw new BadRequestException("账户状态错误");
    }
}