package com.fayu.user.controller;

import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.domain.vo.FriendRequestVO;
import com.fayu.user.domain.vo.FriendVO;
import com.fayu.user.server.IUserFriendServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "好友关系")
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {
    private final IUserFriendServer userFriendServer;

    @PostMapping("/apply")
    @Operation(summary = "发送好友申请，等待对方同意")
    public FriendRequestVO apply(@Valid @RequestBody ApplyFormDTO dto) {
        return userFriendServer.apply(dto);
    }

    @PostMapping("/verify")
    @Operation(summary = "校验当前用户与对方是否允许单聊")
    public boolean verify(@RequestBody VerifyMsgDTO dto) {
        return userFriendServer.verify(dto);
    }

    @PostMapping("/requests/{requestId}/accept")
    public FriendRequestVO accept(@PathVariable("requestId") Long requestId) {
        return userFriendServer.accept(requestId);
    }

    @PostMapping("/requests/{requestId}/reject")
    public FriendRequestVO reject(@PathVariable("requestId") Long requestId) {
        return userFriendServer.reject(requestId);
    }

    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("friendId") Long friendId) {
        userFriendServer.deleteFriend(friendId);
    }

    @GetMapping("/requests")
    public List<FriendRequestVO> requests(@RequestParam(defaultValue = "incoming", name = "direction") String direction,
                                         @RequestParam(required = false, name = "beforeId") Long beforeId,
                                         @RequestParam(defaultValue = "50", name = "limit") int limit) {
        return userFriendServer.requests(direction, beforeId, limit);
    }

    @GetMapping("/list")
    public List<FriendVO> friends(@RequestParam(defaultValue = "0", name = "afterId") long afterId,
                                 @RequestParam(defaultValue = "50", name = "limit") int limit) {
        return userFriendServer.friends(afterId, limit);
    }

    @GetMapping("/search")
    public FriendVO search(@RequestParam(required = false, name = "account") String account,
                           @RequestParam(required = false, name = "userId") Long userId) {
        return userFriendServer.search(account, userId);
    }
}
