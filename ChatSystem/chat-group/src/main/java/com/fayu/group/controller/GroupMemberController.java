package com.fayu.group.controller;

import com.fayu.group.domain.dto.ApplyGroupDTO;
import com.fayu.group.server.IGroupMemberService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/group/member")
@RequiredArgsConstructor
public class GroupMemberController {

    private final IGroupMemberService groupMemberService;


    /**
     * 当前申请入群不需要通过
     * @param applyGroupDTO
     */
    @PostMapping("apply")
    @Operation(summary = "申请入群")
    public void apply(@RequestBody ApplyGroupDTO applyGroupDTO){
        groupMemberService.apply(applyGroupDTO);
    }

}
