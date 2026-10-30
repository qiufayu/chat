package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.SaveMsgDTO;
import com.fayu.message.domain.po.Message;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.vo.SaveMsgVO;

public interface IMessageServer extends IService<Message> {

    SaveMsgVO saveMsg(SaveMsgDTO saveMsgDTO);

    ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(Long conversationId, Long seq);
}