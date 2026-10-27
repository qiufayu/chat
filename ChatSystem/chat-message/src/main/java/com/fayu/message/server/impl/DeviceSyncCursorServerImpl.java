package com.fayu.message.server.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fayu.message.domain.po.DeviceSyncCursor;
import com.fayu.message.mapper.DeviceSyncCursorMapper;
import com.fayu.message.server.IDeviceSyncCursorServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSyncCursorServerImpl extends ServiceImpl<DeviceSyncCursorMapper, DeviceSyncCursor> implements IDeviceSyncCursorServer {
}