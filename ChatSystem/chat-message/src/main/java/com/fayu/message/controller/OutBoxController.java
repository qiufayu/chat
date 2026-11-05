package com.fayu.message.controller;


import com.fayu.common.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.common.domain.dto.SaveOutboxDTO;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.message.server.IMessageOutboxServer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/outbox")
@RequiredArgsConstructor
public class OutBoxController {
    private final IMessageOutboxServer messageOutboxServer;

    @PostMapping("/save")
    public SaveMsgVO saveOutbox(@RequestBody SaveOutboxDTO saveOutboxDTO){
        return messageOutboxServer.saveOutbox(saveOutboxDTO);
    }

    @PostMapping("/update/status")
    public boolean updateStatusToPass(@RequestBody OutBoxUpdateStatusDTO statusDTO){
        return messageOutboxServer.updateStatus(statusDTO);
    }
    @PostMapping("/update/status/retry")
    public boolean updateStatusToRetry(@RequestBody OutBoxUpdateStatusDTO statusDTO){
        return messageOutboxServer.updateStatusToRetry(statusDTO);
    }
    @GetMapping("/retry/list")
    public List<MessageOutbox> getRetryLists(){
        return messageOutboxServer.getRetryLists();
    }
    @GetMapping("/retry/lock")
    public boolean getRetryLock(@RequestParam("outboxId") Long outboxId, @RequestParam("lockedBy") String lockedBy){
        return messageOutboxServer.getRetryLock(outboxId,lockedBy);
    }
    @PostMapping("/retry/unlock")
    public void unlockRetry(@RequestParam("outboxId") Long outboxId,
                            @RequestParam("lockedBy") String lockedBy){
        messageOutboxServer.unlockRetry(outboxId,lockedBy);
    }
}