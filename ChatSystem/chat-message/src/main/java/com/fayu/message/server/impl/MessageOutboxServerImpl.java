package com.fayu.message.server.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fayu.message.domain.po.MessageOutbox;
import com.fayu.message.mapper.MessageOutboxMapper;
import com.fayu.message.server.IMessageOutboxServer;
import org.springframework.stereotype.Service;

/**
 * 消息可靠投递事件表 服务实现类
 */
@Service
public class MessageOutboxServerImpl extends ServiceImpl<MessageOutboxMapper, MessageOutbox> implements IMessageOutboxServer {
}