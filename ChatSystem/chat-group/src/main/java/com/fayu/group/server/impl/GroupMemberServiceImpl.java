package com.fayu.group.server.impl;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.nacos.shaded.io.grpc.internal.JsonUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.api.client.UserClient;
import com.fayu.api.domain.dto.UserInfoDTO;
import com.fayu.common.exception.BaseException;
import com.fayu.common.utils.UserContext;
import com.fayu.group.domain.dto.ApplyGroupDTO;
import com.fayu.group.domain.dto.UpdateGroupDTO;
import com.fayu.group.domain.po.GroupInfo;
import com.fayu.group.domain.po.GroupMember;
import com.fayu.group.domain.vo.CreateGroupVO;
import com.fayu.group.enums.GroupStatus;
import com.fayu.group.enums.MemberStatus;
import com.fayu.group.enums.RoleType;
import com.fayu.group.mapper.GroupMemberMapper;
import com.fayu.group.server.IGroupInfoServer;
import com.fayu.group.server.IGroupMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.fayu.common.constant.RabbitmqConstant.GROUP_EXCHANGE;
import static com.fayu.common.constant.RabbitmqConstant.KEY_ADD_MEMBER;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember> implements IGroupMemberService {

    private final UserClient userClient;
    private final RabbitTemplate rabbitTemplate;
    private final IGroupInfoServer groupInfoServer;

    @Override
    @GlobalTransactional
    public void apply(ApplyGroupDTO applyGroupDTO) {
        Long userId = UserContext.getUser();

        String groupNo = applyGroupDTO.getGroupNO();

        GroupInfo groupInfo = groupInfoServer.lambdaQuery().eq(GroupInfo::getGroupNo, groupNo).one();
        if (!groupInfo.getStatus().equals(GroupStatus.NORMAL.getValue())
                || groupInfo.getMaxMembers() <= groupInfo.getMemberCount()
        ){
            throw new BaseException("添加群聊失败",500);
        }


        UserInfoDTO info = userClient.info();
        if (info == null){
            throw new BaseException("业务错误",500);
        }
        GroupMember groupMember = GroupMember.builder()
                .groupNo(groupNo)
                .userId(userId)
                .role(RoleType.MEMBER.getValue())
                .nickname(info.getNickname())
                .muteUntil(null)
                .joinTime(LocalDateTime.now())
                .status(MemberStatus.NORMAL.getValue())
                .updatedAt(LocalDateTime.now())
                .build();

        boolean succeed = save(groupMember);
        if (!succeed){
            throw new BaseException("业务错误",500);
        }
        UpdateGroupDTO updateGroupDTO = new UpdateGroupDTO();
        updateGroupDTO.setGroupNo(groupNo);
        updateGroupDTO.setIsAddMember(true);

        rabbitTemplate.convertAndSend(GROUP_EXCHANGE,KEY_ADD_MEMBER,updateGroupDTO);
        log.debug("发送消息到队列:{}", updateGroupDTO);
    }


}
