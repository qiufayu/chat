package com.fayu.api.client;

import com.fayu.api.domain.dto.UserInfoDTO;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "chat-user")
public interface UserClient {
    @GetMapping("/user/info")
    UserInfoDTO info();

    @PostMapping("/friend/verify")
    boolean verifyAndCache(@RequestBody VerifyMsgDTO verifyMsgDTO);
}
