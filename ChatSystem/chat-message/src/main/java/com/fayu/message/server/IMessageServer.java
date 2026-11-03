package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.SaveMsgAndUpdateConversationDTO;
import com.fayu.common.domain.dto.SaveOutboxDTO;
import com.fayu.message.domain.po.Message;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.vo.SaveMsgVO;

public interface IMessageServer extends IService<Message> {

    SaveMsgVO saveOutbox(SaveOutboxDTO saveOutboxDTO);

    ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(Long conversationId, Long seq);

    void saveMsgAndUpdateConversation(SaveMsgAndUpdateConversationDTO dto);
}