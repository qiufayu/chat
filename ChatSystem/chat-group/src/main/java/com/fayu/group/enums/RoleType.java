package com.fayu.group.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

@Getter
public enum RoleType {
    MEMBER(0, "成员"),
    ADMIN(1, "管理员"),
    OWNER(2,"群主"),
    ;
    @EnumValue
    int value;
    String desc;

    RoleType(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static RoleType of(int value) {
        if (value == 0) {
            return MEMBER;
        }
        if (value == 1) {
            return ADMIN;
        }
        if (value == 2){
            return OWNER;
        }
        throw new BadRequestException("账户状态错误");
    }
}