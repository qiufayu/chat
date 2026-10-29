package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.message.domain.dto.ChatRequestMessageDTO;
import com.fayu.message.domain.dto.SaveMsgDTO;
import com.fayu.message.domain.po.Message;
import com.fayu.message.domain.vo.ChatResponseMessageVO;
import com.fayu.message.domain.vo.SaveMsgVO;

public interface IMessageServer extends IService<Message> {

    SaveMsgVO saveMsg(SaveMsgDTO saveMsgDTO);

    ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(Long conversationId, Long seq);
}