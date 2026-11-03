package com.fayu.common.constant;

public class RabbitmqConstant {
    public static final String GROUP_ADD_MEMBER_QUEUE = "group.add.member.queue";
    public static final String GROUP_EXCHANGE = "group.topic";
    public static final String KEY_ADD_MEMBER = "add.member";

    public static final String SEND_MESSAGE_QUEUE = "send.message.queue";

    public static final String MESSAGE_EXCHANGE = "message.exchange";
    public static final String KEY_SEND_MESSAGE = "send.message";

    public static final String RETRY_MESSAGE_QUEUE = "retry.message.queue";
    public static final String KEY_RETRY_MESSAGE = "retry.message";

    public static final String OFF_MESSAGE_QUEUE = "off.message.queue";
    public static final String KEY_SAVE_OFF_MESSAGE = "off.message";

    public static final String GET_OFF_MESSAGE_QUEUE = "get.off.message.queue";
    public static final String KEY_GET_OFF_MESSAGE = "get.off.message";

    public static final String SENDER_SELF_SYNC_QUEUE = "sender.self.sync.queue";
    public static final String KEY_SENDER_SELF_SYNC = "sender.self.sync";

    public static final String OUTBOX_CREATE_QUEUE = "outbox.create.queue";
    public static final String OUTBOX_SENT_QUEUE = "outbox.sent.queue";
    public static final String OUTBOX_RETRY_QUEUE = "outbox.retry.queue";
    public static final String KEY_CREATE_OUTBOX = "create.outbox";
    public static final String KEY_UPDATE_STATUS = "update.status";
    public static final String KEY_UPDATE_STATUS_TO_RETRY = "update.status.to.retry";

    public static final String CONVERSATION_QUEUE = "conversation.queue";
    public static final String KEY_UPDATE_CONVERSATION = "update.conversation";

    public static final String USER_CONVERSATION_QUEUE = "user.conversation.queue";
    public static final String KEY_CREATE_USER_CONVERSATION = "create.user.conversation";

    public static final String DEVICE_SYNC_CURSOR_QUEUE = "device.sync.cursor.queue";
    public static final String KEY_ADVANCE_DEVICE_CURSOR = "advance.device.cursor";

    public static final String MESSAGE_QUEUE = "message.queue";
    public static final String KEY_CREATE_MESSAGE = "create.message";

}