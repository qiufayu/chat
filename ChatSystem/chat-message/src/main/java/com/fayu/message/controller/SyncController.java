package com.fayu.message.controller;

import com.fayu.common.domain.dto.DeliveryAckDTO;
import com.fayu.common.domain.dto.ReadAckDTO;
import com.fayu.common.domain.vo.IntervalSyncBatchVO;
import com.fayu.common.domain.vo.SyncBatchVO;
import com.fayu.common.domain.vo.SyncSummaryVO;
import com.fayu.message.server.ISyncServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
public class SyncController {

    private final ISyncServer syncServer;

    @GetMapping("/summary")
    public List<SyncSummaryVO> summary(@RequestParam("userId") Long userId,
                                       @RequestParam("deviceId") String deviceId) {
        return syncServer.getSummary(userId, deviceId);
    }

    @GetMapping("/messages")
    public SyncBatchVO messages(@RequestParam("userId") Long userId,
                                @RequestParam("deviceId") String deviceId,
                                @RequestParam("conversationId") Long conversationId,
                                @RequestParam(value = "afterSeq", required = false) Long afterSeq,
                                @RequestParam(value = "limit", required = false) Integer limit) {
        return syncServer.pull(userId, deviceId, conversationId, afterSeq, limit);
    }

    @PostMapping("/delivery/ack")
    public boolean deliveryAck(@RequestParam("userId") Long userId,
                               @RequestBody DeliveryAckDTO ack) {
        return syncServer.ackDelivery(userId, ack);
    }

    @PostMapping("/read/ack")
    public boolean readAck(@RequestParam("userId") Long userId,
                           @RequestBody ReadAckDTO ack) {
        return syncServer.ackRead(userId, ack);
    }

    /**
     * 客户端主动区间同步，不走socket，每次拉取只能最多取100条
     * @param deviceId
     * @param conversationId
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/interval/sync")
    public SyncBatchVO intervalSyncMessages(@RequestParam("deviceId") String deviceId,
                                                    @RequestParam("conversationId") Long conversationId,
                                                    @RequestParam("start") Long start,
                                                    @RequestParam("end") Long end
                                                    ){
        log.info("客户端请求同步区间消息，deviceId={}, conversationId={}, start={}, end={}", deviceId, conversationId, start, end);
        return syncServer.intervalSyncMessages(deviceId,conversationId,start,end);
    }
}
