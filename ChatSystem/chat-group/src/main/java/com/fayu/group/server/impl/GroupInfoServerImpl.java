package com.fayu.group.server.impl;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.api.client.UserClient;
import com.fayu.api.domain.dto.UserInfoDTO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.common.utils.UserContext;
import com.fayu.group.domain.dto.CreateGroupDTO;
import com.fayu.group.domain.dto.UpdateGroupDTO;
import com.fayu.group.domain.po.GroupInfo;
import com.fayu.group.domain.po.GroupMember;
import com.fayu.group.domain.vo.CreateGroupVO;
import com.fayu.group.enums.GroupStatus;
import com.fayu.group.enums.MemberStatus;
import com.fayu.group.enums.MuteStatus;
import com.fayu.group.enums.RoleType;
import com.fayu.group.mapper.GroupInfoMapper;
import com.fayu.group.mapper.GroupMemberMapper;
import com.fayu.group.server.IGroupInfoServer;
import com.fayu.group.server.IGroupMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import static com.fayu.common.constant.RedisConstant.GROUP_CRE_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInfoServerImpl extends ServiceImpl<GroupInfoMapper, GroupInfo> implements IGroupInfoServer {

    private final RedisIdWorker redisIdWorker;
    private final UserClient userClient;
    private final GroupMemberMapper groupMemberMapper;

    @Override
    @Transactional
    public CreateGroupVO cretaeGroup(CreateGroupDTO createGroupDTO) {
        String groupNo = redisIdWorker.generateId(GROUP_CRE_ID);
        Long userId = UserContext.getUser();
        GroupInfo groupInfo = GroupInfo.builder()
                .groupNo(groupNo)
                .groupType(createGroupDTO.getGroup_type())
                .avatar(defaultAvatar())
                .name(createGroupDTO.getName())
                .description(createGroupDTO.getDescription())
                .maxMembers(createGroupDTO.getMax_members())
                .ownerId(userId)
                .status(GroupStatus.NORMAL.getValue())
                .muteAll(MuteStatus.FALSE.getValue())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        save(groupInfo);

        UserInfoDTO userInfo = userClient.info();
        if (userInfo == null){
            throw new BaseException("创建失败",500);
        }

        GroupMember groupMember = GroupMember.builder()
                .groupNo(groupNo)
                .userId(userId)
                .role(RoleType.OWNER.getValue())
                .nickname(userInfo.getNickname())
                .muteUntil(null)
                .joinTime(LocalDateTime.now())
                .status(MemberStatus.NORMAL.getValue())
                .updatedAt(LocalDateTime.now())
                .build();

        groupMemberMapper.insert(groupMember);
        CreateGroupVO createGroupVO = new CreateGroupVO();
        createGroupVO.setGroupNO(groupNo);
        return createGroupVO;
    }

    @Override

    public void updateGroupInfo(UpdateGroupDTO updateGroupDTO) {
        String groupNo = updateGroupDTO.getGroupNo();
        boolean exists = lambdaQuery().eq(GroupInfo::getGroupNo, groupNo).exists();
        if (!exists){
            throw new BaseException("群聊不存在",500);
        }
        LambdaUpdateChainWrapper<GroupInfo> eq = lambdaUpdate()
                .eq(GroupInfo::getGroupNo, groupNo);

        if (updateGroupDTO.getName() != null){
            eq.set(GroupInfo::getName,updateGroupDTO.getName());
        }
        if (updateGroupDTO.getDescription() != null){
            eq.set(GroupInfo::getDescription,updateGroupDTO.getDescription());
        }
        if (updateGroupDTO.getAvatar() != null){
            eq.set(GroupInfo::getAvatar,updateGroupDTO.getAvatar());
        }
        if (updateGroupDTO.getJoinType() != null){
            eq.set(GroupInfo::getJoinType,updateGroupDTO.getJoinType());
        }
        if (updateGroupDTO.getMaxMembers() != null){
            eq.set(GroupInfo::getMaxMembers,updateGroupDTO.getMaxMembers());
        }
        if (Boolean.TRUE.equals(updateGroupDTO.getIsAddMember())){
            eq.setSql("member_count = member_count + 1")
                    .apply("member_count < max_members");
        }
        if (Boolean.TRUE.equals(updateGroupDTO.getIsDelMember())){
            eq.setSql("member_count = member_count - 1 ")
                    .apply("member_count > 0");
        }
        if (updateGroupDTO.getMuteAll() != null){
            eq.set(GroupInfo::getMuteAll,updateGroupDTO.getMuteAll());
        }
        if (updateGroupDTO.getStatus() != null){
            eq.set(GroupInfo::getStatus,updateGroupDTO.getStatus());
        }
        eq.set(GroupInfo::getUpdatedAt,LocalDateTime.now());

        boolean update = eq.update();
        if (! update){
            throw new BaseException("群信息修改失败",500);
        }
    }

    private String defaultAvatar(){
        return "avatar";
    }
}
