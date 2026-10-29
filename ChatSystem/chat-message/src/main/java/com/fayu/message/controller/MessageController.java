package com.fayu.message.controller;

import com.fayu.message.domain.dto.ChatRequestMessageDTO;
import com.fayu.message.domain.dto.SaveMsgDTO;
import com.fayu.message.domain.vo.ChatResponseMessageVO;
import com.fayu.message.domain.vo.ConversationVO;
import com.fayu.message.domain.vo.SaveMsgVO;
import com.fayu.message.server.IConversationServer;
import com.fayu.message.server.IMessageServer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/msg")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageServer messageServer;

    @PostMapping("/save")
    public SaveMsgVO saveMsg(@RequestBody SaveMsgDTO saveMsgDTO){
        return messageServer.saveMsg(saveMsgDTO);
    }

    @GetMapping("/info")
    public ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(
            @RequestParam("conversationId") Long conversationId ,
            @RequestParam("seq") Long seq)
    {
        return messageServer.getMessageInfo(conversationId,seq);
    }
}
