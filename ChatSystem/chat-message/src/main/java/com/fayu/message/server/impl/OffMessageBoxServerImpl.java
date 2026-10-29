package com.fayu.message.server.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.message.domain.po.OffMessageBox;
import com.fayu.message.mapper.OffMessageBoxMapper;
import com.fayu.message.server.IOffMessageBoxServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户离线消息收件箱 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OffMessageBoxServerImpl extends ServiceImpl<OffMessageBoxMapper, OffMessageBox> implements IOffMessageBoxServer {

}