package com.fayu.user.controller;

import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.server.IUserFriendServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "盆友处理器")
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

    private final IUserFriendServer userFriendServer;


    /**
     * 当前实现：
     *      发送好友申请就可以成为好友
     */
    @PostMapping("/apply")
    @Operation(summary = "申请好友接口")
    public void apply(@RequestBody ApplyFormDTO applyFormDTO){
        userFriendServer.apply(applyFormDTO);
    }

    @PostMapping("/verify")
    public boolean verify(@RequestBody VerifyMsgDTO verifyMsgDTO){
        return userFriendServer.verify(verifyMsgDTO);
    }
}
