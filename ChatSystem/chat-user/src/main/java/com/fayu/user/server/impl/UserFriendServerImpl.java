package com.fayu.user.server.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.exception.UnauthorizedException;
import com.fayu.common.utils.UserContext;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.domain.po.FriendRequest;
import com.fayu.user.domain.po.User;
import com.fayu.user.domain.po.UserFriend;
import com.fayu.user.domain.vo.FriendRequestVO;
import com.fayu.user.domain.vo.FriendVO;
import com.fayu.user.mepper.FriendRequestMapper;
import com.fayu.user.mepper.UserFriendMapper;
import com.fayu.user.mepper.UserMapper;
import com.fayu.user.server.IUserFriendServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.fayu.user.enums.FriendRequestStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFriendServerImpl extends ServiceImpl<UserFriendMapper, UserFriend> implements IUserFriendServer {
    private final UserMapper userMapper;
    private final FriendRequestMapper requestMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FriendRequestVO apply(ApplyFormDTO dto) {
        Long userId = currentUser();
        if (dto == null || (dto.getMessage() != null && dto.getMessage().length() > 100)) {
            throw new BaseException("申请说明不能超过100个字符", 400);
        }
        User target = findTarget(dto.getAccount(), dto.getFriendId());
        Long friendId = target.getId();
        lockPair(userId, friendId, true);
        requireNotBlocked(userId, friendId);
        if (baseMapper.countUsablePair(userId, friendId) > 0) {
            throw new BaseException("双方已经是好友", 409);
        }
        LocalDateTime now = LocalDateTime.now();
        FriendRequest request = requestMapper.findPending(userId, friendId);
        if (request != null && request.getExpiresAt().isAfter(now)) {
            return requestMapper.findView(request.getId());
        }
        if (request != null) finish(request, EXPIRED.name(), now);
        request = new FriendRequest();
        request.setApplicantId(userId);
        request.setReceiverId(friendId);
        request.setMessage(dto.getMessage());
        request.setStatus(PENDING.name());
        request.setCreatedAt(now);
        request.setExpiresAt(now.plusDays(7));
        requestMapper.insert(request);
        return requestMapper.findView(request.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FriendRequestVO accept(Long requestId) {
        return decide(requestId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FriendRequestVO reject(Long requestId) {
        return decide(requestId, false);
    }

    private FriendRequestVO decide(Long requestId, boolean accept) {
        Long userId = currentUser();
        if (requestId == null || requestId <= 0) throw new BaseException("申请编号无效", 400);
        FriendRequest request = requestMapper.selectById(requestId);
        if (request == null) throw new BaseException("好友申请不存在", 404);
        if (!userId.equals(request.getReceiverId())) throw new BaseException("只能处理发给自己的申请", 403);
        lockPair(request.getApplicantId(), userId, false);
        request = requestMapper.selectOne(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getId, requestId).last("FOR UPDATE"));
        if (!PENDING.name().equals(request.getStatus())) return requestMapper.findView(requestId);
        LocalDateTime now = LocalDateTime.now();
        if (!request.getExpiresAt().isAfter(now)) {
            finish(request, EXPIRED.name(), now);
        } else if (!accept) {
            finish(request, REJECTED.name(), now);
        } else {
            requireActive(userMapper.lockById(request.getApplicantId()));
            requireActive(userMapper.lockById(userId));
            requireNotBlocked(request.getApplicantId(), userId);
            baseMapper.restoreFriend(request.getApplicantId(), userId);
            baseMapper.restoreFriend(userId, request.getApplicantId());
            // Finish crossed requests too; an old request must never restore a deleted friendship.
            requestMapper.finishPending(request.getApplicantId(), userId, ACCEPTED.name(), now);
            invalidateAfterCommit(request.getApplicantId(), userId);
        }
        return requestMapper.findView(requestId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void deleteFriend(Long friendId) {
        Long userId = currentUser();
        lockPair(userId, friendId, false);
        baseMapper.deletePair(userId, friendId);
        requestMapper.finishPending(userId, friendId, CANCELLED.name(), LocalDateTime.now());
        invalidateAfterCommit(userId, friendId);
    }

    @Override
    public boolean verify(VerifyMsgDTO dto) {
        Long userId = currentUser();
        if (dto == null || dto.getToId() == null || dto.getToId() <= 0) {
            throw new BaseException("对方用户编号无效", 400);
        }
        if (dto.getSenderId() != null && !userId.equals(dto.getSenderId())) {
            throw new BaseException("不能冒用其他用户校验好友", 403);
        }
        return !userId.equals(dto.getToId()) && baseMapper.countUsablePair(userId, dto.getToId()) > 0;
    }

    @Override
    public List<FriendRequestVO> requests(String direction, Long beforeId, int limit) {
        Long userId = currentUser();
        if (!"incoming".equals(direction) && !"outgoing".equals(direction)) {
            throw new BaseException("direction 只能为 incoming 或 outgoing", 400);
        }
        if (beforeId != null && beforeId <= 0) throw new BaseException("分页游标无效", 400);
        List<FriendRequestVO> result = requestMapper.listRequests(userId, "incoming".equals(direction), beforeId, pageSize(limit));
        LocalDateTime now = LocalDateTime.now();
        result.forEach(request -> {
            if (PENDING.name().equals(request.getStatus()) && !request.getExpiresAt().isAfter(now)) {
                request.setStatus(EXPIRED.name());
            }
        });
        return result;
    }

    @Override
    public List<FriendVO> friends(long afterId, int limit) {
        Long userId = currentUser();
        if (afterId < 0) throw new BaseException("分页游标无效", 400);
        return baseMapper.listFriends(userId, afterId, pageSize(limit));
    }

    @Override
    public FriendVO search(String account, Long userId) {
        currentUser();
        User user = findTarget(account, userId);
        requireActive(user);
        FriendVO result = new FriendVO();
        result.setFriendId(user.getId());
        result.setAccount(user.getAccount());
        result.setNickname(user.getNickname());
        result.setAvatar(user.getAvatar());
        return result;
    }

    private User findTarget(String account, Long id) {
        boolean hasAccount = account != null && !account.isBlank();
        if (hasAccount == (id != null) || (id != null && id <= 0) || (hasAccount && account.trim().length() > 20)) {
            throw new BaseException("对方账号和用户编号必须且只能提供一个", 400);
        }
        User user = id != null ? userMapper.selectById(id) : userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getAccount, account.trim()));
        if (user == null) throw new BaseException("用户不存在", 404);
        return user;
    }

    /** Serialize friendship mutations, including crossed requests, in a fixed lock order. */
    private void lockPair(Long first, Long second, boolean activeRequired) {
        if (second == null || second <= 0 || Objects.equals(first, second)) {
            throw new BaseException("不能对自己操作好友关系", 400);
        }
        User low = userMapper.lockById(Math.min(first, second));
        User high = userMapper.lockById(Math.max(first, second));
        if (low == null || high == null) throw new BaseException("用户不存在", 404);
        if (activeRequired) {
            requireActive(low);
            requireActive(high);
        }
    }

    private void requireActive(User user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BaseException("账号不可用", 403);
    }

    private void requireNotBlocked(Long first, Long second) {
        if (baseMapper.countBlockedPair(first, second) > 0) throw new BaseException("存在拉黑关系，无法添加好友", 403);
    }

    private void finish(FriendRequest request, String status, LocalDateTime now) {
        request.setStatus(status);
        request.setHandledAt(now);
        requestMapper.updateById(request);
    }

    private Long currentUser() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) throw new UnauthorizedException("请先登录");
        return userId;
    }

    private int pageSize(int limit) {
        if (limit < 1 || limit > 100) throw new BaseException("limit 必须在1到100之间", 400);
        return limit;
    }

    private void invalidateAfterCommit(Long first, Long second) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // Legacy Redis sets no longer authorize sending. Never cache a positive permission.
                    redisTemplate.delete(List.of("user:friend:" + first, "user:friend:" + second));
                } catch (RuntimeException e) {
                    log.warn("清理旧好友缓存失败，不影响数据库好友校验 userId={}, friendId={}", first, second, e);
                }
            }
        });
    }
}
