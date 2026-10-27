package com.fayu.user.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.domain.po.UserFriend;

public interface IUserFriendServer extends IService<UserFriend> {
    void apply(ApplyFormDTO applyFormDTO);

    boolean verify(VerifyMsgDTO verifyMsgDTO);
}
