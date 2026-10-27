package com.fayu.connection.code;

import com.fayu.connection.domain.po.Message;
import com.fayu.connection.domain.po.RequestMessage;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.netty.channel.ChannelHandler.*;


@Slf4j
@Component
@Sharable
@RequiredArgsConstructor
public class MessageCodecSharable extends MessageToMessageDecoder<TextWebSocketFrame> {

    private final MessageUtil messageUtil;

    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame frame, List<Object> list) throws Exception {
        String trim = frame.text().trim();
        RequestMessage rsqMsg = messageUtil.getRsqMsg(trim);
        Message<?> convert = messageUtil.convert(rsqMsg);
        list.add(convert);
    }

}
