package com.fayu.connection.enums;

import com.fayu.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 消息类型：1文字、2图片、3文件
 */
@Getter
public enum MessageContentType {
    TEXT(1, "文字"),
    IMAGE(2, "图片"),
    FILE(3, "文件"),
    ERROR(4,"错误")
    ;
    int value;
    String desc;

    MessageContentType(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static MessageContentType of(int value) {
        if (value == 1) {
            return TEXT;
        }
        if (value == 2) {
            return IMAGE;
        }
        if (value == 3) {
            return FILE;
        }
        throw new BadRequestException("消息类型错误");
    }
}