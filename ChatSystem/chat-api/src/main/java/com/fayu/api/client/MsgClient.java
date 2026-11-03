package com.fayu.api.client;

import com.fayu.common.domain.dto.*;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.common.domain.vo.SyncBatchVO;
import com.fayu.common.domain.vo.SyncSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "chat-message")
public interface MsgClient {

    @GetMapping("/msg")
    void start();

    @PostMapping("/msg/save")
    SaveMsgVO saveMsg(@RequestBody SaveOutboxDTO saveOutboxDTO);

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

    @GetMapping("/sync/summary")
    List<SyncSummaryVO> getSyncSummary(@RequestParam("userId") Long userId,
                                       @RequestParam("deviceId") String deviceId);

    @GetMapping("/sync/messages")
    SyncBatchVO getSyncMessages(@RequestParam("userId") Long userId,
                                @RequestParam("deviceId") String deviceId,
                                @RequestParam("conversationId") Long conversationId,
                                @RequestParam("afterSeq") Long afterSeq,
                                @RequestParam("limit") Integer limit);

    @PostMapping("/sync/delivery/ack")
    boolean ackDelivery(@RequestParam("userId") Long userId,
                        @RequestBody DeliveryAckDTO ack);

    @PostMapping("/sync/read/ack")
    boolean ackRead(@RequestParam("userId") Long userId,
                    @RequestBody ReadAckDTO ack);

    @PostMapping("/msg/insert")
    void saveMsgAndUpdateConversation(@RequestBody SaveMsgAndUpdateConversationDTO dto);

}
