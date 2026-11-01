package com.fayu.connection.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 消息可靠投递事件状态枚举
 */
@Getter
public enum OutboxStatusEnum {

    /**
     * 待发送
     */
    PENDING(0, "待发送"),

    /**
     * 已发送
     */
    SENT(2, "已发送"),

    /**
     * 待重试
     */
    RETRY_WAIT(3, "待重试"),

    /**
     * 最终失败
     */
    FAILED(4, "最终失败");

    /**
     * 数据库存储值
     */
    @JsonValue
    private final Integer code;

    /**
     * 描述
     */
    private final String desc;

    OutboxStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 状态码
     * @return 枚举，找不到返回 null
     */
    public static OutboxStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OutboxStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 根据 code 获取描述
     *
     * @param code 状态码
     * @return 描述，找不到返回 "未知"
     */
    public static String descOf(Integer code) {
        OutboxStatusEnum statusEnum = of(code);
        return statusEnum == null ? "未知" : statusEnum.getDesc();
    }
}
