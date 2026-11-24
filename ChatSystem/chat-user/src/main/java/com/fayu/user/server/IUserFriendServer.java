package com.fayu.user.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.domain.po.UserFriend;
import com.fayu.user.domain.vo.FriendRequestVO;
import com.fayu.user.domain.vo.FriendVO;
import java.util.List;

public interface IUserFriendServer extends IService<UserFriend> {
    FriendRequestVO apply(ApplyFormDTO applyFormDTO);

    boolean verify(VerifyMsgDTO verifyMsgDTO);

    FriendRequestVO accept(Long requestId);
    FriendRequestVO reject(Long requestId);
    void deleteFriend(Long friendId);
    List<FriendRequestVO> requests(String direction, Long beforeId, int limit);
    List<FriendVO> friends(long afterId, int limit);
    FriendVO search(String account, Long userId);
}
