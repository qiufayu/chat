package com.fayu.connection.session;

import com.fayu.connection.factory.SingleSessionFactory;
import io.netty.channel.Channel;

import java.util.Collection;

public interface SingleSession {
    void bind(Channel channel, Long userId, String deviceId);
    void unBind(Channel channel, Long userId);
    Channel getChannel(Long userId);
    Collection<Channel> getChannels(Long userId);
    Long getUserId(Channel channel);
    String getDeviceId(Channel channel);
    void setAttr(Channel channel, String key,Object value);
    Object getAttr(Channel channel,String key);
}
