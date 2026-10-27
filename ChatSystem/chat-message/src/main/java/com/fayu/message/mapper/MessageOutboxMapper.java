package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.message.domain.po.MessageOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息可靠投递事件表 Mapper 接口
 */
@Mapper
public interface MessageOutboxMapper extends BaseMapper<MessageOutbox> {
}