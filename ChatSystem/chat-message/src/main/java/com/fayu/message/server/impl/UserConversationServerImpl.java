package com.fayu.message.server.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.message.domain.po.UserConversation;
import com.fayu.message.mapper.UserConversationMapper;
import com.fayu.message.server.IUserConversationServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserConversationServerImpl extends ServiceImpl<UserConversationMapper, UserConversation> implements IUserConversationServer {
}