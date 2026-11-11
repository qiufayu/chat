package com.fayu.message.server.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fayu.common.domain.dto.DeliveryAckDTO;
import com.fayu.common.domain.dto.ReadAckDTO;
import com.fayu.common.domain.vo.IntervalSyncBatchVO;
import com.fayu.common.domain.vo.SyncBatchVO;
import com.fayu.common.domain.vo.SyncMessageVO;
import com.fayu.common.domain.vo.SyncSummaryVO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.UserContext;
import com.fayu.message.domain.po.Conversation;
import com.fayu.message.domain.po.DeviceSyncCursor;
import com.fayu.message.domain.po.UserConversation;
import com.fayu.message.domain.vo.ConversationSeqOfDeviceVO;
import com.fayu.message.mapper.ConversationMapper;
import com.fayu.message.mapper.DeviceSyncCursorMapper;
import com.fayu.message.mapper.MessageMapper;
import com.fayu.message.mapper.UserConversationMapper;
import com.fayu.message.server.ISyncServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncServerImpl implements ISyncServer {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final UserConversationMapper userConversationMapper;
    private final ConversationMapper conversationMapper;
    private final DeviceSyncCursorMapper deviceSyncCursorMapper;
    private final MessageMapper messageMapper;

    @Override
    public List<SyncSummaryVO> getSummary(Long userId, String deviceId) {
        requireUserAndDevice(userId, deviceId);
        List<UserConversation> conversations = userConversationMapper.selectList(
                new LambdaQueryWrapper<UserConversation>()
                        .eq(UserConversation::getUserId, userId)
        );
        List<SyncSummaryVO> result = new ArrayList<>(conversations.size());
        List<Long> conversationIds = conversations.stream().map(UserConversation::getConversationId).toList();
        List<ConversationSeqOfDeviceVO> seqOfDeviceVOS = conversationMapper.selectConversationSeqOfDevice(userId, deviceId, conversationIds);
        Map<Long, ConversationSeqOfDeviceVO> ofDeviceVOMap = seqOfDeviceVOS.stream()
                .filter(vo -> vo != null && vo.getConversationId() != null)
                .collect(Collectors.toMap(
                        ConversationSeqOfDeviceVO::getConversationId,
                        v -> v, (existing,
                                 replacement) -> existing)
                );
        for (UserConversation conversation : conversations) {
            Long conversationId = conversation.getConversationId();
            ConversationSeqOfDeviceVO vo = ofDeviceVOMap.get(conversationId);
            long lastSeq = 0L;
            long lastSyncedSeq = 0L;
            long unreadCount = 0L;
            String lastMessage = null;
            if (vo != null){
                lastSeq = vo.getLastSeq() == null ? 0L : vo.getLastSeq();
                lastSyncedSeq = vo.getLastSyncedSeq() == null ? 0L : vo.getLastSyncedSeq();
                unreadCount = vo.getLastReadSeq() == null ? lastSeq : Math.max(0L, lastSeq - vo.getLastReadSeq());
                lastMessage = vo.getLastMessage() == null ? "" : vo.getLastMessage();
            }
            result.add(SyncSummaryVO.builder()
                    .conversationId(conversationId)
                    .lastSeq(lastSeq)
                    .lastSyncedSeq(lastSyncedSeq)
                    .unreadCount(unreadCount)
                    .lastMessage(lastMessage)
                    .pendingCount(Math.max(0L, lastSeq - lastSyncedSeq))
                    .build());
        }
        return result;
    }

    @Override
    public SyncBatchVO pull(Long userId, String deviceId, Long conversationId, Long afterSeq, Integer limit) {
        requireUserAndDevice(userId, deviceId);
        requireMembership(userId, conversationId);

        DeviceSyncCursor cursor = getOrCreateCursor(userId, deviceId, conversationId);
        long cursorSeq = cursor.getLastSyncedSeq() == null ? 0L : cursor.getLastSyncedSeq();
        // 客户端携带的 afterSeq 只能用于重放当前游标之后的数据，不能跳过游标之前的消息。
        // 设备接收进度的唯一可信来源是数据库中的 last_synced_seq。
        long startSeq = cursorSeq;
        int pageSize = normalizeLimit(limit);
        List<SyncMessageVO> messages = messageMapper.listSyncMessages(
                userId, conversationId, startSeq, pageSize);
        Long nextSeq = messages.isEmpty() ? startSeq : messages.get(messages.size() - 1).getSeq();
        return SyncBatchVO.builder()
                .conversationId(conversationId)
                .nextSeq(nextSeq)
                .hasMore(messages.size() == pageSize)
                .messages(messages)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean ackDelivery(Long userId, DeliveryAckDTO ack) {
        requireUserAndDevice(userId, ack == null ? null : ack.getDeviceId());
        if (ack.getConversationId() == null || ack.getAckSeq() == null || ack.getAckSeq() < 0) {
            throw new BaseException("送达确认参数错误", 400);
        }
        requireMembership(userId, ack.getConversationId());
        Conversation conversation = conversationMapper.selectById(ack.getConversationId());
        if (conversation == null || conversation.getLastSeq() == null
                || ack.getAckSeq() > conversation.getLastSeq()) {
            throw new BaseException("送达确认序号超出会话范围", 400);
        }
        return deviceSyncCursorMapper.advance(
                userId, ack.getDeviceId(), ack.getConversationId(), ack.getAckSeq()) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean ackRead(Long userId, ReadAckDTO ack) {
        if (userId == null || ack == null || ack.getConversationId() == null
                || ack.getReadSeq() == null || ack.getReadSeq() < 0) {
            throw new BaseException("已读确认参数错误", 400);
        }
        requireMembership(userId, ack.getConversationId());
        Conversation conversation = conversationMapper.selectById(ack.getConversationId());
        if (conversation == null || conversation.getLastSeq() == null
                || ack.getReadSeq() > conversation.getLastSeq()) {
            throw new BaseException("已读确认序号超出会话范围", 400);
        }
        requireUserAndDevice(userId, ack.getDeviceId());
        DeviceSyncCursor cursor = deviceSyncCursorMapper.findOne(
                userId, ack.getDeviceId(), ack.getConversationId());
        if (cursor == null || cursor.getLastSyncedSeq() == null
                || cursor.getLastSyncedSeq() < ack.getReadSeq()) {
            throw new BaseException("未确认收到该消息，不能标记为已读", 400);
        }
        return userConversationMapper.advanceReadSeq(
                userId, ack.getConversationId(), ack.getReadSeq()) > 0;
    }

    @Override
    public SyncBatchVO intervalSyncMessages(String deviceId, Long conversationId, Long start, Long end) {
        log.debug("客户端主动请求同步 deviceId={} conversationId={} start={} end={}",deviceId,conversationId,start,end);
        Long userId = UserContext.getUser();
        verify(start,end);
        requireUserAndDevice(userId, deviceId);
        requireMembership(userId, conversationId);
        int pageSize = normalizeLimit(start,end);
        List<SyncMessageVO> messages = messageMapper.intervalSyncMessages(
                userId, conversationId, start, end,pageSize);
        Long nextSeq = messages.isEmpty() ? end : messages.get(messages.size() - 1).getSeq();
        log.debug("客户端主动请求同步成功 deviceId={} conversationId={} start={} end={} 同步数据长度={}",deviceId,conversationId,start,end,messages.size());
        return SyncBatchVO.builder()
                .conversationId(conversationId)
                .nextSeq(nextSeq)
                .hasMore(messages.size() == pageSize)
                .messages(messages)
                .build();
    }

    private DeviceSyncCursor getOrCreateCursor(Long userId, String deviceId, Long conversationId) {
        DeviceSyncCursor cursor = deviceSyncCursorMapper.findOne(userId, deviceId, conversationId);
        if (cursor != null) {
            return cursor;
        }
        deviceSyncCursorMapper.advance(userId, deviceId, conversationId, 0L);
        cursor = deviceSyncCursorMapper.findOne(userId, deviceId, conversationId);
        if (cursor == null) {
            throw new BaseException("创建设备同步游标失败", 500);
        }
        return cursor;
    }

    private void requireMembership(Long userId, Long conversationId) {
        if (conversationId == null || userConversationMapper.findOne(userId, conversationId) == null) {
            throw new BaseException("无权访问该会话", 403);
        }
    }

    private void requireUserAndDevice(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank() || deviceId.length() > 64) {
            throw new BaseException("用户或设备参数错误", 400);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeLimit(Long start , Long end) {
        if (end < start){
            return DEFAULT_LIMIT;
        }
        return Math.toIntExact(end - start);
    }
    private void verify(Long start, Long end){
        if (start == null || end == null || start <= 0 || start > end){
            throw new BaseException("查询参数错误",400);
        }
    }
}
