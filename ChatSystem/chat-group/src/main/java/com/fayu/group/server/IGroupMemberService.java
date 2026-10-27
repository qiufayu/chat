package com.fayu.group.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.group.domain.dto.ApplyGroupDTO;
import com.fayu.group.domain.po.GroupMember;

public interface IGroupMemberService extends IService<GroupMember> {
    void apply(ApplyGroupDTO applyGroupDTO);
}
