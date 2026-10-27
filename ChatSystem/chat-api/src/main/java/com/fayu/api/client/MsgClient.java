package com.fayu.api.client;

import com.fayu.api.domain.dto.SaveMsgDTO;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.api.domain.vo.ConversationVO;
import com.fayu.api.domain.vo.SaveMsgVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "chat-message")
public interface MsgClient {
    @PostMapping("/msg/save")
    SaveMsgVO saveMsg(@RequestBody SaveMsgDTO saveMsgDTO);
}
