package com.fayu.message.server.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.message.domain.dto.UpdateConversationDTO;
import com.fayu.message.domain.po.Conversation;
import com.fayu.message.domain.po.UserConversation;
import com.fayu.message.domain.vo.ConversationVO;
import com.fayu.message.enums.ConversationStatus;
import com.fayu.message.enums.ConversationType;
import com.fayu.message.enums.YesNoStatus;
import com.fayu.message.mapper.ConversationMapper;
import com.fayu.message.server.IConversationServer;
import com.fayu.message.server.IUserConversationServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServerImpl extends ServiceImpl<ConversationMapper, Conversation> implements IConversationServer {

    private final IUserConversationServer userConversationServer;
    private final RedisIdWorker redisIdWorker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO getConversation(Long sendId, Long toId , Long groupId) {
        LambdaQueryChainWrapper<Conversation> wrapper = lambdaQuery();
        Long lowId = null;
        Long highId = null;
        if (sendId != null && toId != null){
            lowId = Math.min(sendId,toId);
            highId = Math.max(sendId,toId);
        }
        if (lowId != null && highId != null){
            wrapper.eq(Conversation::getUserLowId,lowId)
                    .eq(Conversation::getUserHighId,highId);
        } else if (groupId != null) {
            wrapper.eq(Conversation::getGroupId,groupId);
        }else {
            throw new BaseException("参数错误",500);
        }
        Conversation conversation = wrapper.one();
        if (conversation == null){
            int conversationType = groupId != null ? ConversationType.GROUP.getValue() : ConversationType.SINGLE.getValue();
            conversation = new Conversation();
            conversation.setConversationType(conversationType)
                    .setStatus(ConversationStatus.NORMAL.getValue())
                    .setLastSeq(0L)
                    .setCreatedAt(LocalDateTime.now())
                    .setUpdatedAt(LocalDateTime.now());
            if (groupId != null){
                conversation.setGroupId(groupId);
            }else{
                conversation.setUserLowId(lowId);
                conversation.setUserHighId(highId);
            }
            boolean flag = false;
            try {
                flag = save(conversation);
            } catch (DuplicateKeyException e) {
                log.warn("并发创建会话冲突，重新查询: lowId={}, highId={}......", lowId, highId);
                throw e;
//                // 重新查一次
//                conversation = wrapper.last("FOR UPDATE").one();
//                if (conversation == null) {
//                    throw new BaseException("获取会话失败，请重试", 500);
//                }
            }
            if (flag){
                ArrayList<UserConversation> userConversations = new ArrayList<>();
                Long conversationId = conversation.getId();
                userConversations.add(createUserConversation(conversationId, sendId));
                userConversations.add(createUserConversation(conversationId, toId));
                boolean pass = userConversationServer.saveBatch(userConversations);
                    if (!pass){
                        throw new BaseException("业务错误",500);
                    }
                    log.debug("创建用户会话 userId1={} , userId2={}",toId,sendId);
            }
        }
        ConversationVO conversationVO = BeanUtil.copyProperties(conversation, ConversationVO.class);
        conversationVO.setConversationId(conversation.getId());
        return conversationVO;
    }
    @Override
    public void initFriendConversation(Long userId1, Long userId2) {

    }

    @Override
    public void updateConversation(UpdateConversationDTO dto) {
        if (dto.getConversationId() == null){
            throw new BaseException("会话更新失败: conversationId 不能为空",500);
        }
        LambdaUpdateChainWrapper<Conversation> wrapper = lambdaUpdate();
        boolean needUpdate = false;
        wrapper.eq(Conversation::getId,dto.getConversationId());
        if (dto.getStatus() != null){
            wrapper.set(Conversation::getStatus,dto.getStatus());
            needUpdate = true;
        }
        if (dto.getOldLastSeq() != null && dto.getNewLastSeq() != null){
            wrapper.set(Conversation::getLastSeq,dto.getNewLastSeq())
                    .apply("last_seq = {0}",dto.getOldLastSeq());
            needUpdate = true;
        }
        if (dto.getLastMessageAt() != null && dto.getLastMessageId() != null){
            wrapper.set(Conversation::getLastMessageAt,dto.getLastMessageAt())
                    .set(Conversation::getLastMessageId,dto.getLastMessageId());
            needUpdate = true;
        }
        if (!needUpdate){
            log.warn("会话 conversationId={} 无更新",dto.getConversationId());
            return;
        }
        wrapper.set(Conversation::getUpdatedAt,LocalDateTime.now());
        boolean update = wrapper.update();
        if (!update){
            log.warn("会话更新失败 conversationId={} 更新失败 可能是会话不存在，也可能是 last_seq 防回退拦截",dto.getConversationId());
        }
    }

    private UserConversation createUserConversation(Long conversationId, Long userId) {
        return UserConversation.builder()
                .conversationId(conversationId)
                .userId(userId)
                .isMuted(YesNoStatus.YES.getValue())
                .pinnedAt(null)
                .isPinned(YesNoStatus.YES.getValue())
                .lastReadSeq(0L)
                .lastReadAt(LocalDateTime.now())
                .settingsVersion(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }


}