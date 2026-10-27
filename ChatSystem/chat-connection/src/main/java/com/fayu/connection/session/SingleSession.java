package com.fayu.connection.session;

import com.fayu.connection.factory.SingleSessionFactory;
import io.netty.channel.Channel;

public interface SingleSession {
    void bind(Channel channel, Long userId);
    void unBind(Channel channel, Long userId);
    Channel getChannel(Long userId);
    Long getUserId(Channel channel);
    void setAttr(Channel channel, String key,Object value);
    Object getAttr(Channel channel,String key);
}
