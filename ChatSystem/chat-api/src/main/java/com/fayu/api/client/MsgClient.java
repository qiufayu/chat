package com.fayu.api.client;

import com.fayu.api.domain.dto.ChatRequestMessageDTO;
import com.fayu.api.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.api.domain.dto.SaveMsgDTO;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.api.domain.po.EventEnvelope;
import com.fayu.api.domain.po.MessageOutbox;
import com.fayu.api.domain.vo.ChatResponseMessageVO;
import com.fayu.api.domain.vo.ConversationVO;
import com.fayu.api.domain.vo.SaveMsgVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@FeignClient(value = "chat-message")
public interface MsgClient {

    @GetMapping("/msg")
    void start();

    @PostMapping("/msg/save")
    SaveMsgVO saveMsg(@RequestBody SaveMsgDTO saveMsgDTO);

    @GetMapping("/msg/info")
    ChatResponseMessageVO<ChatRequestMessageDTO> getMessageInfo(
            @RequestParam("conversationId") Long conversationId ,
            @RequestParam("seq") Long seq);

    @PostMapping("/outbox/update/status")
    boolean updateStatus(@RequestBody OutBoxUpdateStatusDTO statusDTO);
    @PostMapping("/outbox/update/status/retry")
    boolean updateStatusToRetry(@RequestBody OutBoxUpdateStatusDTO statusDTO);

    @GetMapping("/outbox/retry/list")
    List<MessageOutbox> getRetryLists();

    @GetMapping("/outbox/retry/lock")
    boolean getRetryLock(@RequestParam("outboxId") Long outboxId, @RequestParam("lockedBy") String lockedBy);

    @PostMapping("/outbox/retry/unlock")
    void unlockRetry(@RequestParam("outboxId") Long outboxId, @RequestParam("lockedBy") String lockedBy);

}
