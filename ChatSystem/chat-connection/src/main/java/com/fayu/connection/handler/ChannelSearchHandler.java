package com.fayu.connection.handler;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.fayu.api.client.MsgClient;
import com.fayu.common.domain.vo.SyncSummaryVO;
import com.fayu.common.utils.UserContext;
import com.fayu.connection.enums.MessageType;
import com.fayu.connection.executor.ChatBusinessExecutor;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.SingleSession;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ChannelSearchHandler extends ChannelInboundHandlerAdapter {

    private final MsgClient msgClient;
    private final ChatBusinessExecutor chatBusinessExecutor;
    private final MessageUtil messageUtil;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        SingleSession session = SingleSessionFactory.getSession();
        if (message instanceof TextWebSocketFrame ping
                && MessageType.PING.toString().equals(ping.text())) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(MessageType.PONG.toString()));
            log.debug("通道 userId={} 接收到心跳数据 PING 回写 PONG....", session.getUserId(ctx.channel()));
            return;
        }
        ctx.fireChannelRead(message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("发生异常，主动断连", cause);
        ctx.close();
    }

    // 连接关闭时执行
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        SingleSession session = SingleSessionFactory.getSession();
        Long userId = session.getUserId(ctx.channel());
        if (userId != null) {
            session.unBind(ctx.channel(), userId);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object env) throws Exception {
        SingleSession singleSession = SingleSessionFactory.getSession();
        Channel channel = ctx.channel();
        log.debug("====== 收到 UserEvent，类型是: env = {}",
                env != null ? env.getClass().getName() : null);

        if (env instanceof WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete) {
            String userIdStr = handshakeComplete.requestHeaders().get("user-info");
            String deviceId = handshakeComplete.requestHeaders().get("device-id");
            log.debug("user-info = {}, device-id = {}", userIdStr, deviceId);
            String requestUri = ((WebSocketServerProtocolHandler.HandshakeComplete) env).requestUri();
            log.debug("request 握手请求 uri = {}", requestUri);

            if (StringUtils.isBlank(userIdStr) || StringUtils.isBlank(deviceId)) {
                log.error("WebSocket 握手失败，userId 或 deviceId 无效！");
                ctx.close();
                return;
            }
            Long userId = Long.parseLong(userIdStr);

            if (!deviceId.isBlank() && deviceId.length() <= 64) {
                singleSession.setAttr(channel, "userId", userId);
                singleSession.setAttr(channel, "deviceId", deviceId);
                singleSession.bind(channel, userId, deviceId);
                UserContext.setUser(userId);
                sendConnectionReady(channel, deviceId);
                chatBusinessExecutor.execute(() -> sendSyncSummary(channel, userId, deviceId));
                log.debug("WebSocket 握手成功！ userId={}, deviceId={}", userId, deviceId);
            } else {
                log.error("WebSocket 握手失败，userId 或 deviceId 无效！ userId={}, deviceId={}", userId, deviceId);
                ctx.close();
            }
        } else if (env instanceof IdleStateEvent event) {
            Long userId = (Long) singleSession.getAttr(channel, "userId");
            if (event.state() == IdleState.READER_IDLE) {
                if (event.isFirst()) {
                    log.warn("用户 userId={} 60秒没收到心跳了，发个探测包试试", userId);
                } else {
                    log.error("用户 userId={} 120秒没收到心跳，自动断开连接", userId);
                    singleSession.unBind(channel, userId);
                    ctx.close();
                }
            }
        }
        super.userEventTriggered(ctx, env);
    }

    private void sendConnectionReady(Channel channel, String deviceId) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "CONNECTION_READY");
        response.put("deviceId", deviceId);
        response.put("serverTime", System.currentTimeMillis());
        channel.writeAndFlush(new TextWebSocketFrame(messageUtil.toJson(response)));
    }

    private void sendSyncSummary(Channel channel, Long userId, String deviceId) {
        try {
            List<SyncSummaryVO> summaries = msgClient.getSyncSummary(userId, deviceId);
            Map<String, Object> response = new HashMap<>();
            response.put("type", "SYNC_SUMMARY");
            response.put("conversations", summaries);
            channel.writeAndFlush(new TextWebSocketFrame(messageUtil.toJson(response)));
        } catch (Exception e) {
            log.error("发送同步摘要失败 userId={}, deviceId={}", userId, deviceId, e);
        }
    }

//    private Long getUserIdFromUri(String uriStr) {
//        String userId = getQueryParam(uriStr, "userId");
//        if (userId == null) {
//            return null;
//        }
//        try {
//            return Long.parseLong(userId);
//        } catch (NumberFormatException e) {
//            throw new IllegalArgumentException("用户ID格式错误", e);
//        }
//    }
//
//    private String getQueryParam(String uriStr, String name) {
//        try {
//            URI uri = new URI(uriStr);
//            String query = uri.getQuery();
//            if (query == null) {
//                return null;
//            }
//            for (String pair : query.split("&")) {
//                String[] parts = pair.split("=", 2);
//                if (parts.length == 2 && name.equals(parts[0])) {
//                    return parts[1];
//                }
//            }
//            return null;
//        } catch (URISyntaxException e) {
//            log.warn("解析 URI 获取参数失败, uri: {}, 错误信息: {}", uriStr, e.getMessage());
//            throw new IllegalArgumentException("非法的 WebSocket 连接 URI: " + uriStr, e);
//        }
//    }
}
