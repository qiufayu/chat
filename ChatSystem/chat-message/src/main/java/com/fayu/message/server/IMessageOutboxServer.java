package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.po.MessageOutbox;

import java.util.List;

/**
 * 消息可靠投递事件表 服务类
 */
public interface IMessageOutboxServer extends IService<MessageOutbox> {

    boolean updateStatusToRetry(OutBoxUpdateStatusDTO statusDTO);

    List<MessageOutbox> getRetryLists();

    boolean getRetryLock(Long outboxId, String lockedBy);

    void unlockRetry(Long outboxId, String lockedBy);

    boolean updateStatus(OutBoxUpdateStatusDTO statusDTO);
}
