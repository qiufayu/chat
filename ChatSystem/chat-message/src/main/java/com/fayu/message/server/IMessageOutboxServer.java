package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.message.domain.po.MessageOutbox;

/**
 * 消息可靠投递事件表 服务类
 */
public interface IMessageOutboxServer extends IService<MessageOutbox> {
}