package com.fayu.message.domain.po;

import com.fayu.api.domain.po.MessageMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    private T payload;              // 业务消息体
    private MessageMetadata metadata; // 消息头/投递元数据
}
