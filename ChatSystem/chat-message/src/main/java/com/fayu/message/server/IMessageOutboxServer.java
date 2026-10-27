package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.message.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.message.domain.po.EventEnvelope;
import com.fayu.message.domain.po.MessageOutbox;

import java.util.List;

/**
 * 消息可靠投递事件表 服务类
 */
public interface IMessageOutboxServer extends IService<MessageOutbox> {
    boolean updateStatus(OutBoxUpdateStatusDTO statusDTO);

    boolean retryMessageUpdateStatus(OutBoxUpdateStatusDTO statusDTO);

    List<MessageOutbox> getRetryLists();
}