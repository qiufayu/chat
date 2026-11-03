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
import com.fayu.common.domain.vo.ConversationVO;
import com.fayu.message.enums.ConversationStatus;
import com.fayu.message.enums.ConversationType;
import com.fayu.message.enums.YesNoStatus;
import com.fayu.message.mapper.ConversationMapper;
import com.fayu.message.server.IConversationServer;
import com.fayu.message.server.IUserConversationServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static com.fayu.common.constant.RabbitmqConstant.KEY_CREATE_USER_CONVERSATION;
import static com.fayu.common.constant.RabbitmqConstant.MESSAGE_EXCHANGE;
import static com.fayu.common.constant.RedisConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServerImpl extends ServiceImpl<ConversationMapper, Conversation> implements IConversationServer {

    private final IUserConversationServer userConversationServer;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Retryable(
            retryFor = {
                    DeadlockLoserDataAccessException.class,
                    DuplicateKeyException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 30,maxDelay = 300,multiplier = 2)
    )
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO getConversation(Long sendId, Long toId , Long groupId) {
        log.debug("查询数据库获取会话信息 sendId={} toId={} groupId={}",sendId,toId,groupId);
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
        int conversationType = groupId != null ? ConversationType.GROUP.getValue() : ConversationType.SINGLE.getValue();
        if (conversation == null){
            conversation = new Conversation();
            conversation
                    .setConversationType(conversationType)
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
                crateUserConversationEvent(sendId, toId, conversation);
            }
        }
        // 添加缓存
        if (ConversationType.SINGLE.getValue() == conversationType){
            saveConversationCache(conversation.getId(),lowId,highId);
        }else{
            saveConversationCache(conversation.getId(),groupId);
        }
        log.debug("添加缓存成功 lowId={}, highId={} conversationId={}", lowId, highId,conversation.getId());

        ConversationVO conversationVO = BeanUtil.copyProperties(conversation, ConversationVO.class);
        conversationVO.setConversationId(conversation.getId());
        return conversationVO;
    }

    private void crateUserConversationEvent(Long sendId, Long toId, Conversation conversation) {
        ArrayList<UserConversation> userConversations = new ArrayList<>();
        Long conversationId = conversation.getId();
        userConversations.add(createUserConversation(conversationId, sendId));
        userConversations.add(createUserConversation(conversationId, toId));
        rabbitTemplate.convertAndSend(MESSAGE_EXCHANGE,KEY_CREATE_USER_CONVERSATION,userConversations);
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
        if (dto.getNewLastSeq() != null){
            wrapper.set(Conversation::getLastSeq,dto.getNewLastSeq())
                    .lt(Conversation::getLastSeq, dto.getNewLastSeq());
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

    private void saveConversationCache(Long conversationId, Long groupId) {
        String key = GROUP_CONVERSATION_ID.formatted(groupId);
        stringRedisTemplate.opsForHash().put(key,"conversationId",conversationId.toString());
    }

    private void saveConversationCache(Long conversationId, Long lowId, Long highId) {
        String key = SINGLE_CONVERSATION_ID.formatted(lowId,highId);
        stringRedisTemplate.opsForHash().put(key,"conversationId",conversationId.toString());
    }

}