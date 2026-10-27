package com.fayu.group.controller;

import com.fayu.group.domain.dto.CreateGroupDTO;
import com.fayu.group.domain.vo.CreateGroupVO;
import com.fayu.group.server.IGroupInfoServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "群聊相关接口")
@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
public class GroupController {

    private final IGroupInfoServer groupInfoServer;

    @PostMapping("create")
    @Operation(summary = "创建群聊")
    public CreateGroupVO createGroup(@RequestBody CreateGroupDTO createGroupDTO) {
        return groupInfoServer.cretaeGroup(createGroupDTO);
    }



}
