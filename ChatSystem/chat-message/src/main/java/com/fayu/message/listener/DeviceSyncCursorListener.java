package com.fayu.message.listener;

import com.fayu.message.domain.dto.AdvanceDeviceCursorDTO;
import com.fayu.message.mapper.DeviceSyncCursorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceSyncCursorListener {

    private final DeviceSyncCursorMapper deviceSyncCursorMapper;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = DEVICE_SYNC_CURSOR_QUEUE, durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE, type = "topic"),
                    key = {KEY_ADVANCE_DEVICE_CURSOR}
            )
    )
    public void advanceDeviceCursorListener(AdvanceDeviceCursorDTO event) {
        if (event == null || event.getUserId() == null || event.getDeviceId() == null
                || event.getConversationId() == null || event.getSeq() == null) {
            log.warn("设备游标推进事件缺少必要字段，忽略: {}", event);
            return;
        }
        try {
            deviceSyncCursorMapper.advance(
                    event.getUserId(), event.getDeviceId(),
                    event.getConversationId(), event.getSeq());
        } catch (Exception e) {
            log.error("设备游标推进失败 userId={}, deviceId={}, conversationId={}, seq={}",
                    event.getUserId(), event.getDeviceId(), event.getConversationId(), event.getSeq(), e);
        }
        log.info("【mq】 推进设备游标 userId={}, deviceId={}, conversationId={}, seq={}",
                event.getUserId(), event.getDeviceId(), event.getConversationId(), event.getSeq());
    }
}