package com.fayu.message.controller;


import com.fayu.message.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.message.domain.po.MessageOutbox;
import com.fayu.message.server.IMessageOutboxServer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/outbox")
@RequiredArgsConstructor
public class OutBoxController {
    private final IMessageOutboxServer messageOutboxServer;

    @PostMapping("/update/status")
    public boolean updateStatus(@RequestBody OutBoxUpdateStatusDTO statusDTO){
        return messageOutboxServer.updateStatus(statusDTO);
    }
    @PostMapping("/retry/message")
    public boolean retryMessageUpdateStatus(@RequestBody OutBoxUpdateStatusDTO statusDTO){
        return messageOutboxServer.retryMessageUpdateStatus(statusDTO);
    }
    @GetMapping("/retry/list")
    public List<MessageOutbox> getRetryLists(){
        return messageOutboxServer.getRetryLists();
    }
}
