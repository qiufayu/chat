package com.fayu.message.controller;

import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.SaveMsgAndUpdateConversationDTO;
import com.fayu.common.domain.dto.SaveOutboxDTO;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.message.server.IMessageServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/msg")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageServer messageServer;

    @GetMapping
    public void start(){
        log.debug("message-server 连接成功");
    }

    @PostMapping("/save")
    public SaveMsgVO saveMsg(@RequestBody SaveOutboxDTO saveOutboxDTO){
        return messageServer.saveOutbox(saveOutboxDTO);
    }

    @GetMapping("/info")
    public ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(
            @RequestParam("conversationId") Long conversationId ,
            @RequestParam("seq") Long seq)
    {
        return messageServer.getMessageInfo(conversationId,seq);
    }

    @PostMapping("/insert")
    public void saveMsgAndUpdateConversation(@RequestBody SaveMsgAndUpdateConversationDTO dto){
        messageServer.saveMsgAndUpdateConversation(dto);
    }
}
