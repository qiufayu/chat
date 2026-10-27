package com.fayu.connection.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fayu.connection.domain.dto.ChatRequestMessageDTO;
import com.fayu.connection.domain.po.Message;
import com.fayu.connection.domain.po.RequestMessage;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.MessageTypeConstant.NOTE;
import static io.netty.channel.ChannelHandler.*;

@Component
@Sharable
public class MessageUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private <T> Message<T> buildMessage(RequestMessage rsqMsg, Class<T> clazz){
        T payload = MAPPER.convertValue(rsqMsg.getPayload(),clazz);
        return Message.<T>builder()
                .id(rsqMsg.getId())
                .type(rsqMsg.getType())
                .clientMessageId(rsqMsg.getClientMessageId())
                .senderId(rsqMsg.getSenderId())
                .payload(payload)
                .timeAt(rsqMsg.getTimeAt())
                .build();
    }

    public Message<?> convert(RequestMessage rsqMsg){
        return switch (rsqMsg.getType()){
            case NOTE -> buildMessage(rsqMsg, ChatRequestMessageDTO.class);
            default -> throw new IllegalArgumentException("未知类型");
        };
    }

    public RequestMessage getRsqMsg(String msg){
        try {
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
