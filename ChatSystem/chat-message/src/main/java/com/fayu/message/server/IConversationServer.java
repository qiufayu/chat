package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.message.domain.dto.UpdateConversationDTO;
import com.fayu.message.domain.po.Conversation;
import com.fayu.message.domain.vo.ConversationVO;


public interface IConversationServer extends IService<Conversation> {
    ConversationVO getConversation(Long sendId, Long toId, Long groupId);

    void initFriendConversation(Long userId1, Long userId2);

    void updateConversation(UpdateConversationDTO dto);
}