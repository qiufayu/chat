package com.fayu.message.server;

import com.fayu.common.domain.dto.DeliveryAckDTO;
import com.fayu.common.domain.dto.ReadAckDTO;
import com.fayu.common.domain.vo.IntervalSyncBatchVO;
import com.fayu.common.domain.vo.SyncBatchVO;
import com.fayu.common.domain.vo.SyncSummaryVO;

import java.util.List;

public interface ISyncServer {

    List<SyncSummaryVO> getSummary(Long userId, String deviceId);

    SyncBatchVO pull(Long userId, String deviceId, Long conversationId, Long afterSeq, Integer limit);

    boolean ackDelivery(Long userId, DeliveryAckDTO ack);

    boolean ackRead(Long userId, ReadAckDTO ack);

    SyncBatchVO intervalSyncMessages( String deviceId, Long conversationId, Long start, Long end);
}
