package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.message.domain.po.Conversation;
import com.fayu.message.domain.vo.ConversationSeqOfDeviceVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ConversationMapper extends BaseMapper<Conversation> {
    List<ConversationSeqOfDeviceVO> selectConversationSeqOfDevice(@Param("userId") Long userId,@Param("deviceId") String deviceId,@Param("conversationIds") List<Long> conversationId);
}