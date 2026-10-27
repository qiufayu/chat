package com.fayu.user.server.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.common.utils.UserContext;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.domain.po.User;
import com.fayu.user.domain.po.UserFriend;
import com.fayu.user.enums.BlockedStatus;
import com.fayu.user.enums.FriendStatus;
import com.fayu.user.mepper.UserFriendMapper;
import com.fayu.user.server.IUserFriendServer;
import com.fayu.user.server.IUserServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.fayu.common.constant.RedisConstant.REMARK_CRE_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFriendServerImpl extends ServiceImpl<UserFriendMapper, UserFriend> implements IUserFriendServer {

    private final IUserServer userServer;
    private final RedisIdWorker redisIdWorker;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(ApplyFormDTO applyFormDTO) {

        String account = applyFormDTO.getAccount();

        Long userId = UserContext.getUser();
        User friend = userServer.lambdaQuery().eq(User::getAccount, account).one();

        boolean exists = lambdaQuery().eq(UserFriend::getUserId, userId).eq(UserFriend::getFriendId, friend.getId()).exists();
        if (exists){
            log.info("userId={} -> friend_id={} 好友链已存在",userId,friend.getId());
            return;
        }

        UserFriend friend1 = UserFriend.builder()
                .userId(userId)
                .friendId(friend.getId())
                .status(FriendStatus.NORMAL.getValue())
                .isBlocked(BlockedStatus.FROZEN.getValue())
                .remark(redisIdWorker.generateId(REMARK_CRE_ID + userId, 1L))
                .createdAt(LocalDateTime.now())
                .build();

        UserFriend friend2 = UserFriend.builder()
                .userId(friend.getId())
                .friendId(userId)
                .status(FriendStatus.NORMAL.getValue())
                .isBlocked(BlockedStatus.FROZEN.getValue())
                .remark(redisIdWorker.generateId(REMARK_CRE_ID + friend.getId(), 1L))
                .createdAt(LocalDateTime.now())
                .build();
        log.info("userId={} -> friend_id={} 建立好友链",userId,friend.getId());
        save(friend1);
        save(friend2);

    }

    @Override
    public boolean verify(VerifyMsgDTO verifyMsgDTO) {
        UserFriend userFriend = lambdaQuery().eq(UserFriend::getUserId, verifyMsgDTO.getSenderId())
                .eq(UserFriend::getFriendId, verifyMsgDTO.getToId()).one();
        if (userFriend == null
                || userFriend.getStatus().equals(FriendStatus.FROZEN.getValue())
                || userFriend.getIsBlocked().equals(BlockedStatus.NORMAL.getValue())
        ){
            return false;
        }
        // TODO 缓存好友关系
        return true;
    }
}
