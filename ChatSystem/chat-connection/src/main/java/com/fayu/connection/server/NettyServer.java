package com.fayu.connection.server;

import com.fayu.connection.code.MessageCodecSharable;
import com.fayu.connection.handler.ChannelSearchHandler;
import com.fayu.connection.handler.ChatMessageHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NettyServer{

    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;
    private Channel channel;

    private final ChannelSearchHandler channelSearchHandler;
    private final ChatMessageHandler chatMessageHandler;
    private final MessageCodecSharable messageDecoder;

    @PostConstruct
    public void start()  {
        boss = new NioEventLoopGroup();
        worker = new NioEventLoopGroup();

        try {
            channel = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel channel) throws Exception {
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            // 开启前缀匹配
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws",true));
                            // 心跳
                            pipeline.addLast(new IdleStateHandler(600,0,0));
                            pipeline.addLast(channelSearchHandler);
                            pipeline.addLast(messageDecoder);
                            pipeline.addLast(chatMessageHandler);

                        }
                    })
                    .bind(9000).sync().channel();
            log.info("Netty 服务启动，端口 9000");
        } catch (InterruptedException e) {
            log.error("Netty 服务启动失败",e);
            throw new RuntimeException(e);
        }

    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (channel != null) channel.close().sync();
        if (boss != null) boss.shutdownGracefully();
        if (worker != null) worker.shutdownGracefully();
    }


}
