package com.fayu.connection.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fayu.api.client.MsgClient;
import com.fayu.api.client.UserClient;
import com.fayu.api.domain.dto.SaveMsgDTO;
import com.fayu.api.domain.po.EventEnvelope;
import com.fayu.api.domain.po.MessageMetadata;
import com.fayu.api.domain.vo.SaveMsgVO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.connection.domain.dto.ChatRequestMessageDTO;
import com.fayu.connection.domain.dto.SaveOffMessageBoxDTO;
import com.fayu.connection.domain.po.Message;
import com.fayu.connection.domain.po.ResponseMessage;
import com.fayu.connection.domain.vo.ChatResponseMessageVO;
import com.fayu.connection.enums.EventType;
import com.fayu.connection.enums.MessageContentType;
import com.fayu.connection.enums.MessageType;
import com.fayu.connection.enums.OffMsgStatus;
import com.fayu.connection.executor.ChatBusinessExecutor;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.SingleSession;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ChatMessageHandler extends SimpleChannelInboundHandler<Message<?>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MsgClient msgClient;
    private final UserClient userClient;
    private final ChatBusinessExecutor chatBusinessExecutor;
    private final RedisIdWorker redisIdWorker;
    private final MessageUtil messageUtil;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message<?> msg) throws Exception {
        log.debug("获取到消息：msg = {}",msg);
        String clientMessageId = msg.getClientMessageId();
        Long timeAt = msg.getTimeAt();
        Long toUserId = msg.getTo();
        SingleSession session = SingleSessionFactory.getSession();
        Long sendUserId = (Long) session.getAttr(ctx.channel(), "userId");
        chatBusinessExecutor.execute(()->{
            try {
                Boolean member = stringRedisTemplate.opsForSet().isMember("user:friend:" + sendUserId, String.valueOf(toUserId));
                if (!Boolean.TRUE.equals(member)){
                    log.debug("对方不上你的朋友，已被拉黑");
                    return;
                }

                EventEnvelope<?> eventEnvelope = saveMsgGetEventEnvelope(msg , MessageContentType.TEXT);

                if (eventEnvelope == null){
                    log.error("服务异常");
                    return;
                }
                ctx.writeAndFlush(new TextWebSocketFrame("ack"));

                MessageMetadata metadata = eventEnvelope.getMetadata();
                // 获取接收者通道
                Channel receiveChannel = session.getChannel(toUserId);
                if (receiveChannel == null) {
                    log.info("用户 userId={} 离线,消息添加至离线消息池 messageId={}", toUserId,metadata.getMessageId());
                    SaveOffMessageBoxDTO offMessageBoxDTO = SaveOffMessageBoxDTO.builder()
                            .messageId(metadata.getMessageId())
                            .userId(toUserId)
                            .conversationId(metadata.getConversationId())
                            .createdAt(LocalDateTime.now())
                            .seq(metadata.getSeq())
                            .status(OffMsgStatus.PENDING.getCode())
                            .build();
                    rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_SAVE_OFF_MESSAGE,offMessageBoxDTO);
                    return;
                }
                log.debug("开始异步发送消息 ，toUserId={} messageId={}",toUserId,metadata.getMessageId());
                rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_SEND_MESSAGE,eventEnvelope);
            } catch (Exception e) {
                log.error("业务处理失败", e);
                sendErrorMsg(ctx, clientMessageId, timeAt);
            }
        });
    }

    private void sendErrorMsg(ChannelHandlerContext ctx, String clientMessageId, Long timeAt) {
        ChatResponseMessageVO messageVO = ChatResponseMessageVO.builder()
                .messageType(MessageContentType.TEXT.getValue())
                .errorContent("消息发送失败 请重新尝试")
                .build();
        ResponseMessage<ChatResponseMessageVO> responseMessageVO = ResponseMessage.<ChatResponseMessageVO>builder()
                .clientMessageId(clientMessageId)
                .type(MessageType.ERROR.toString())
                .payload(messageVO)
                .timeAt(timeAt).build();
        String json = messageUtil.toJson(responseMessageVO);
        ctx.writeAndFlush(new TextWebSocketFrame(json));
    }

    private EventEnvelope<?> saveMsgGetEventEnvelope(Message<?> msg, MessageContentType contentType) {
        if (msg == null || msg.getPayload() == null) {
            throw new BaseException("消息为空",500);
        }
        Object obgMsg = msg.getPayload();
        String content = getContentByType(obgMsg, contentType);
        SaveMsgDTO saveMsgDTO = SaveMsgDTO.builder()
                .senderId(msg.getSenderId())
                .toId(msg.getTo())
                .clientMessageId(msg.getClientMessageId())
                .messageType(contentType.getValue())
                .build();
        if (contentType.equals(MessageContentType.TEXT)){
            saveMsgDTO.setTextContent(content);
        }else{
            //TODO 处理文件图片
            saveMsgDTO.setAttachmentId(null);
        }

        SaveMsgVO saveMsgVO = msgClient.saveMsg(saveMsgDTO);
        if (saveMsgVO == null){
            return null;
        }
        MessageMetadata metadata = MessageMetadata.builder()
                .conversationId(saveMsgVO.getConversationId())
                .sendId(msg.getSenderId())
                .toId(msg.getTo())
                .messageId(saveMsgVO.getMessageId())
                .messageType(contentType.getValue())
                .clientMessageId(msg.getClientMessageId())
                .eventType(EventType.MESSAGE_CREATE.toString())
                .seq(saveMsgVO.getSeq())
                .timestamp(saveMsgVO.getTimeAt())
                .build();
        return EventEnvelope.builder()
                .metadata(metadata)
                .payload(msg.getPayload()).build();
    }

    private String getContentByType(Object msgPayload,MessageContentType contentType){
        switch (contentType) {
            case TEXT -> {
                ChatRequestMessageDTO payload  = (ChatRequestMessageDTO) msgPayload;
                return payload.getContent();
            }
            case IMAGE -> {
                log.info("处理图片逻辑.....");
                return null;
            }
            case FILE -> {
                log.info("处理文件逻辑.....");
                return null;
            }
            default -> {
                return null;
            }
        }
    }

}
