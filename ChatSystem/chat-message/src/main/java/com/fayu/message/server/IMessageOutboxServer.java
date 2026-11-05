package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.dto.SaveOutboxDTO;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.SaveMsgVO;

import java.util.List;

public interface IMessageOutboxServer extends IService<MessageOutbox> {

    SaveMsgVO saveOutbox(SaveOutboxDTO saveOutboxDTO);

    boolean updateStatusToRetry(OutBoxUpdateStatusDTO statusDTO);

    List<MessageOutbox> getRetryLists();

    boolean getRetryLock(Long outboxId, String lockedBy);

    void unlockRetry(Long outboxId, String lockedBy);

    boolean updateStatus(OutBoxUpdateStatusDTO statusDTO);
}