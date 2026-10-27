package com.fayu.common.constant;

public class RedisConstant {

    public static String PREFIX = "chat:";

    /**
     * id
     */
    public static String USER_REG_ID = PREFIX + "user:reg:id";
    public static String GROUP_CRE_ID = PREFIX + "group:cre:id";

    /**
     * remark 随机备注
     */
    public static String REMARK_CRE_ID = PREFIX + "remark:cre:";

    /**
     * base
     */
    public static String LOGIN_ACCESS_TOKEN = PREFIX + "login:access:token:";
    public static String LOGIN_REFRESH_TOKEN = PREFIX + "login:refresh:token:";
}
