package com.fayu.api.client;

import com.fayu.api.domain.dto.OutBoxUpdateStatusDTO;
import com.fayu.api.domain.dto.SaveMsgDTO;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.api.domain.po.EventEnvelope;
import com.fayu.api.domain.po.MessageOutbox;
import com.fayu.api.domain.vo.ConversationVO;
import com.fayu.api.domain.vo.SaveMsgVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "chat-message")
public interface MsgClient {
    @PostMapping("/msg/save")
    SaveMsgVO saveMsg(@RequestBody SaveMsgDTO saveMsgDTO);


    @PostMapping("/outbox/update/status")
    boolean updateStatus(@RequestBody EventEnvelope<?> eventEnvelope);

    @PostMapping("/outbox/retry/message")
    boolean retryMessageUpdateStatus(@RequestBody OutBoxUpdateStatusDTO statusDTO);

    @GetMapping("/retry/list")
    List<MessageOutbox> getRetryLists();
}
