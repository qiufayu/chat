package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.message.domain.po.Message;
import com.fayu.message.domain.po.MessageInfoRow;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;

import java.util.List;

public interface MessageMapper extends BaseMapper<Message> {
    List<MessageOutbox> queryRetryMessages();

    MessageInfoRow getMessageInfo(Long conversationId, Long seq);
}
