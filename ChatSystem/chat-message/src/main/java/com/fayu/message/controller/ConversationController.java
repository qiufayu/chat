package com.fayu.message.controller;

import com.fayu.common.domain.vo.ConversationVO;
import com.fayu.message.server.IConversationServer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conversation")
@RequiredArgsConstructor
public class ConversationController {
    private final IConversationServer conversationServer;

    @PostMapping("/init/friend")
    public void initFriendConversation(@RequestParam(name = "userId1") Long userId1, @RequestParam(name = "userId2") Long userId2){
        conversationServer.initFriendConversation(userId1,userId2);
    }

    @GetMapping("/conversation")
    public ConversationVO getConversation(@RequestParam(value = "sendId" ,required = true) Long sendId,
                                          @RequestParam(value = "toId" , required = false) Long toId,
                                          @RequestParam(value = "groupId",required = false) Long groupId
    ){
        return conversationServer.getConversation(sendId,toId,groupId);
    }

}
