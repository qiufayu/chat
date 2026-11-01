package com.fayu.connection.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.dto.DeliveryAckDTO;
import com.fayu.common.domain.dto.ReadAckDTO;
import com.fayu.common.domain.dto.SyncRequestDTO;
import com.fayu.connection.domain.po.Message;
import com.fayu.connection.domain.po.RequestMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.MessageTypeConstant.NOTE;
import static io.netty.channel.ChannelHandler.*;

@Slf4j
@Component
@Sharable
public class MessageUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    private <T> Message<T> buildMessage(RequestMessage rsqMsg, Class<T> clazz){
        T payload = MAPPER.convertValue(rsqMsg.getPayload(),clazz);
        return Message.<T>builder()
                .id(rsqMsg.getId())
                .type(rsqMsg.getType())
                .to(rsqMsg.getTo())
                .clientMessageId(rsqMsg.getClientMessageId())
                .deviceId(rsqMsg.getDeviceId())
                .senderId(rsqMsg.getSenderId())
                .payload(payload)
                .timeAt(rsqMsg.getTimeAt())
                .build();
    }

    private <T> Message<T> buildControlMessage(RequestMessage rsqMsg, Class<T> clazz){
        Object source = rsqMsg.getPayload() == null || rsqMsg.getPayload().isNull()
                ? rsqMsg : rsqMsg.getPayload();
        T payload = MAPPER.convertValue(source, clazz);
        return Message.<T>builder()
                .id(rsqMsg.getId())
                .type(rsqMsg.getType())
                .to(rsqMsg.getTo())
                .clientMessageId(rsqMsg.getClientMessageId())
                .deviceId(rsqMsg.getDeviceId())
                .senderId(rsqMsg.getSenderId())
                .payload(payload)
                .timeAt(rsqMsg.getTimeAt())
                .build();
    }

    public Message<?> convert(RequestMessage rsqMsg){
        if (rsqMsg == null || rsqMsg.getType() == null) {
            throw new IllegalArgumentException("消息类型不能为空");
        }
        String type = rsqMsg.getType().toUpperCase();
        return switch (type){
            case "NOTE", "CHAT" -> buildMessage(rsqMsg, ChatRequestMessageDTO.class);
            case "SYNC_REQUEST" -> buildControlMessage(rsqMsg, SyncRequestDTO.class);
            case "DELIVERY_ACK" -> buildControlMessage(rsqMsg, DeliveryAckDTO.class);
            case "READ_ACK" -> buildControlMessage(rsqMsg, ReadAckDTO.class);
            default -> throw new IllegalArgumentException("未知类型: " + rsqMsg.getType());
        };
    }

    public RequestMessage getRsqMsg(String msg){
        try {
            log.debug("msg = {}",msg);
            return MAPPER.readValue(msg, RequestMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("未知消息");
        }
    }

    public String toJson(Object o){
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败："+"{"+ o.toString() +"}",e);
        }
    }

}
