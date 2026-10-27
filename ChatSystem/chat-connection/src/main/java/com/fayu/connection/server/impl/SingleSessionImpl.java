package com.fayu.connection.server.impl;


import com.fayu.connection.session.SingleSession;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SingleSessionImpl implements SingleSession {
    public static Map<Long, Channel> userChannelMap = new ConcurrentHashMap<>();
    public static Map<Channel,Long> channelUserMap  = new ConcurrentHashMap<>();

    @Override
    public void bind(Channel channel, Long userId) {
        userChannelMap.put(userId,channel);
        channelUserMap.put(channel,userId);
        log.debug("通道保存成功 userId={}",userId);
    }

    @Override
    public void unBind(Channel channel, Long userId) {
        userChannelMap.remove(userId,channel);
        channelUserMap.remove(channel,userId);
    }

    @Override
    public Channel getChannel(Long userId) {
        Channel channel = userChannelMap.get(userId);
        log.debug("获取通道 userId={} {}",userId,channel != null? "成功":"失败");
        return channel;
    }

    @Override
    public Long getUserId(Channel channel) {
        return channelUserMap.get(channel);
    }


    @Override
    public void setAttr(Channel channel, String key, Object value) {
        channel.attr(AttributeKey.valueOf(key)).set(value);
    }

    @Override
    public Object getAttr(Channel channel, String key) {
        return channel.attr(AttributeKey.valueOf(key)).get();
    }

}
