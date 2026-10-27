package com.fayu.connection.handler;

import com.fayu.common.utils.UserContext;
import com.fayu.connection.enums.MessageType;
import com.fayu.connection.factory.GroupSessionFactory;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.session.GroupSession;
import com.fayu.connection.session.SingleSession;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Component
@ChannelHandler.Sharable
public class ChannelSearchHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object o) throws Exception {
        SingleSession session = SingleSessionFactory.getSession();
        if (o instanceof TextWebSocketFrame ping){
            if (MessageType.PING.toString().equals(ping.text())){
                ctx.channel().writeAndFlush(new TextWebSocketFrame(MessageType.PONG.toString()));
                log.debug("通道 userId={} 接收到心跳数据 PING 回写 PONG....",session.getUserId(ctx.channel()));
                return;
            }
        }
        ctx.fireChannelRead(o);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("发生异常 主动断连：",cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object env) throws Exception{
        SingleSession singleSession = SingleSessionFactory.getSession();
        GroupSession groupSession = GroupSessionFactory.getSession();
        Channel channel = ctx.channel();
        log.debug("====== 收到 UserEvent，类型是: env = {}",env!=null?env.getClass().getName():null);
        if (env instanceof WebSocketServerProtocolHandler.HandshakeComplete){
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = (WebSocketServerProtocolHandler.HandshakeComplete) env;
            String requestUri = handshakeComplete.requestUri();
            Long userId = getUserIdFromUri(requestUri);
            if (userId != null){
                singleSession.setAttr(channel, "userId",userId);
                singleSession.bind(channel,userId);
                log.debug("WebSocket 握手成功！ 获取到 userId={}",userId);
                UserContext.setUser(userId);
            }else {
                log.error("WebSocket 握手失败！ uri={}",requestUri);
            }
        } else if (env instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) env;
            Long userId = (Long) singleSession.getAttr(channel, "userId");
            IdleState state = event.state();
            if (state == IdleState.READER_IDLE){
                if (event.isFirst()){
                    log.warn("用户 userId={} 60秒没收到心跳了，发个探测包试试",userId);
                }else {
                    log.error("用户 userId={} 120秒没收到心跳，自动断开连接",userId);
                    singleSession.unBind(channel,userId);
                    // TODO 删除用户群通道
                    ctx.close();
                }
            }
        }
        super.userEventTriggered(ctx,env);
    }


    private Long getUserIdFromUri(String uriStr){
        try {
            URI uri = new URI(uriStr);
            String query = uri.getQuery();
            if (query != null && query.contains("userId=")){
                return Long.parseLong(query.split("userId=")[1].split("&")[0]);
            }
        } catch (URISyntaxException e) {
            log.warn("解析 URI 获取 userId 失败, uri: {}, 错误信息: {}", uriStr, e.getMessage());
            throw new IllegalArgumentException("非法的 WebSocket 连接 URI: " + uriStr, e);
        }
        return null;
    }

}
