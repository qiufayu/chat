package com.fayu.group.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.group.domain.dto.CreateGroupDTO;
import com.fayu.group.domain.dto.UpdateGroupDTO;
import com.fayu.group.domain.po.GroupInfo;
import com.fayu.group.domain.vo.CreateGroupVO;

public interface IGroupInfoServer extends IService<GroupInfo> {
    CreateGroupVO cretaeGroup(CreateGroupDTO createGroupDTO);
    void updateGroupInfo(UpdateGroupDTO updateGroupDTO);
}
