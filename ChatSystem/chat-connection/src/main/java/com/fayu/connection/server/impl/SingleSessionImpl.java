package com.fayu.connection.server.impl;


import com.fayu.connection.session.SingleSession;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SingleSessionImpl implements SingleSession {
    /** 一个用户可以同时在多个设备在线，设备编号是连接的唯一维度。 */
    public static Map<Long, Map<String, Channel>> userChannelMap = new ConcurrentHashMap<>();
    public static Map<Channel,Long> channelUserMap  = new ConcurrentHashMap<>();
    public static Map<Channel,String> channelDeviceMap = new ConcurrentHashMap<>();

    @Override
    public void bind(Channel channel, Long userId, String deviceId) {
        Map<String, Channel> deviceChannels = userChannelMap.computeIfAbsent(
                userId, ignored -> new ConcurrentHashMap<>());
        Channel previous = deviceChannels.put(deviceId, channel);
        if (previous != null && previous != channel) {
            channelUserMap.remove(previous, userId);
            channelDeviceMap.remove(previous, deviceId);
            if (previous.isOpen()) {
                previous.close();
            }
        }
        channelUserMap.put(channel,userId);
        channelDeviceMap.put(channel,deviceId);
        log.debug("通道保存成功 userId={}, deviceId={}",userId, deviceId);
    }

    @Override
    public void unBind(Channel channel, Long userId) {
        String deviceId = channelDeviceMap.get(channel);
        Map<String, Channel> deviceChannels = userChannelMap.get(userId);
        if (deviceChannels != null && deviceId != null) {
            deviceChannels.remove(deviceId, channel);
            if (deviceChannels.isEmpty()) {
                userChannelMap.remove(userId, deviceChannels);
            }
        }
        channelUserMap.remove(channel,userId);
        if (deviceId != null) {
            channelDeviceMap.remove(channel, deviceId);
        }
    }

    @Override
    public Channel getChannel(Long userId) {
        Collection<Channel> channels = getChannels(userId);
        Channel channel = channels.stream().findFirst().orElse(null);
        log.debug("获取通道 userId={} {}",userId,channel != null? "成功":"失败");
        return channel;
    }

    @Override
    public Collection<Channel> getChannels(Long userId) {
        Map<String, Channel> channels = userChannelMap.get(userId);
        if (channels == null || channels.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(channels.values());
    }

    @Override
    public Long getUserId(Channel channel) {
        return channelUserMap.get(channel);
    }

    @Override
    public String getDeviceId(Channel channel) {
        return channelDeviceMap.get(channel);
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
