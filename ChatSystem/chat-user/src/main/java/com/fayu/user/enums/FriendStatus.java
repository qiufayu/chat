package com.fayu.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum FriendStatus {
    FROZEN(0, "删除"),
    NORMAL(1, "正常"),
    ;
    @EnumValue
    int value;
    String desc;
    FriendStatus(int value, String desc){
        this.value=value;
        this.desc=desc;
    }
    public static FriendStatus of(int value) {
        if (value == 0) {
            return FROZEN;
        }
        if (value == 1) {
            return NORMAL;
        }
        throw new BadRequestException("账户状态错误");
    }
}
