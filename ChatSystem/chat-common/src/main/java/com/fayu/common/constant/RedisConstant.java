package com.fayu.common.constant;

public class RedisConstant {

    public static String PREFIX = "chat:";

    /**
     * id
     */
    public static String USER_REG_ID = PREFIX + "user:reg:id";
    public static String GROUP_CRE_ID = PREFIX + "group:cre:id";
    public static String MESSAGE_ID = PREFIX + "message:id";

    public static String SEQ_INC = PREFIX +"seq:" +"%s";

    /**
     * remark 随机备注
     */
    public static String REMARK_CRE_ID = PREFIX + "remark:cre:";

    /**
     * base
     */
    public static String LOGIN_ACCESS_TOKEN = PREFIX + "login:access:token:";
    public static String LOGIN_REFRESH_TOKEN = PREFIX + "login:refresh:token:";

    /**
     * cache
     */
    public static String SINGLE_CONVERSATION_ID = PREFIX + "user_pair:" + "%s_%s";
    public static String GROUP_CONVERSATION_ID = PREFIX + "group:" + "%s";
}
