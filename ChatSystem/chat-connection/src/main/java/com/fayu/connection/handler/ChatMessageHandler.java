package com.fayu.connection.handler;

import com.fayu.api.client.MsgClient;
import com.fayu.api.client.UserClient;
import com.fayu.api.domain.dto.SaveMsgDTO;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.api.domain.vo.ConversationVO;
import com.fayu.api.domain.vo.SaveMsgVO;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.connection.domain.dto.ChatRequestMessageDTO;
import com.fayu.connection.domain.po.Message;
import com.fayu.connection.domain.po.ResponseMessage;
import com.fayu.connection.domain.vo.ChatResponseMessageVO;
import com.fayu.connection.enums.ConversationType;
import com.fayu.connection.enums.MessageContentType;
import com.fayu.connection.enums.MessageType;
import com.fayu.connection.enums.YesNoStatus;
import com.fayu.connection.executor.ChatBusinessExecutor;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.SingleSession;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ChatMessageHandler extends SimpleChannelInboundHandler<Message<?>> {

    private final MsgClient msgClient;
    private final UserClient userClient;
    private final ChatBusinessExecutor chatBusinessExecutor;
    private final RedisIdWorker redisIdWorker;
    private final MessageUtil messageUtil;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message<?> msg) throws Exception {

        if (!(msg.getPayload() instanceof ChatRequestMessageDTO payload)){
            log.error("note 非法消息：{}",msg);
            return;
        }
        log.debug("获取到消息：msg = {}",msg);
        Long toUserId = payload.getTo();
        String content = payload.getContent();
        String clientMessageId = msg.getClientMessageId();
        Long timeAt = msg.getTimeAt();
        SingleSession session = SingleSessionFactory.getSession();
        Long sendUserId = (Long) session.getAttr(ctx.channel(), "userId");
        chatBusinessExecutor.execute(()->{
            try {
                // TODO 验证好友关系  先redis查询缓存
                VerifyMsgDTO verifyMsgDTO = new VerifyMsgDTO();
                verifyMsgDTO.setSenderId(sendUserId);
                verifyMsgDTO.setToId(toUserId);
                boolean pass = userClient.verifyAndCache(verifyMsgDTO);
                if (!pass){
                    log.error("好友关系验证不通过");
                    ctx.writeAndFlush(new TextWebSocketFrame("非好友状态或已被拉黑"));
                    return;
                }

                SaveMsgDTO saveMsgDTO = SaveMsgDTO.builder()
                        .senderId(sendUserId)
                        .toId(toUserId)
                        .clientMessageId(clientMessageId)
                        .messageType(MessageContentType.TEXT.getValue())
                        .textContent(content)
                        .attachmentId(null)
                        .build();
                //  保存信息并且获取会话信息
                SaveMsgVO saveMsgVO = msgClient.saveMsg(saveMsgDTO);
                Long conversationId = saveMsgVO.getConversationId();
                ChatResponseMessageVO ackMsgVO = ChatResponseMessageVO.builder()
                        .conversationId(conversationId)
                        .seq(saveMsgVO.getSeq())
                        .build();
                ResponseMessage<ChatResponseMessageVO> responseMessageVO = ResponseMessage.<ChatResponseMessageVO>builder()
                        .clientMessageId(clientMessageId)
                        .type(MessageType.ERROR.toString())
                        .payload(ackMsgVO)
                        .timeAt(timeAt).build();
                String json = messageUtil.toJson(responseMessageVO);

                ctx.writeAndFlush(new TextWebSocketFrame("ack"));

                // 获取接收者通道
                Channel receiveChannel = session.getChannel(toUserId);
                if (receiveChannel == null){
                    // TODO 离线消息mq异步存储
                    log.info("用户 userId={} 离线",toUserId);

                    return;
                }
                ChatResponseMessageVO failAckMsgVO = ChatResponseMessageVO.builder()
                        .messageId(saveMsgVO.getMessageId())
                        .conversationId(conversationId)
                        .seq(saveMsgVO.getSeq())
                        .conversationType(ConversationType.SINGLE.getValue())
                        .sendId(sendUserId)
                        .toId(toUserId)
                        .messageType(MessageContentType.TEXT.getValue())
                        .content(content)
                        .build();
                String msgStr = messageUtil.toJson(failAckMsgVO);
                ChannelFuture channelFuture = receiveChannel.writeAndFlush(new TextWebSocketFrame(msgStr));
                channelFuture.addListener(new GenericFutureListener<Future<? super Void>>() {
                    @Override
                    public void operationComplete(Future<? super Void> future) throws Exception {
                        log.debug("发送消息到 userId={}",toUserId);
                    }
                });

            } catch (Exception e) {
                log.error("业务处理失败", e);
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
        });
    }
}
