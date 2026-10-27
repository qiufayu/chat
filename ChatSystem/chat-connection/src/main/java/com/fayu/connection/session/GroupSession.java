package com.fayu.connection.session;

import com.fayu.connection.domain.po.Group;

import java.util.Set;

public interface GroupSession {
    Group cretaeGroup(String groupNo, Set<Long> members);
    Group joinGroup(String groupNo,Long memberId);
    Group removeMember(String groupNo,Long memberId);
    Group removeGroup(String groupNo);
    Set<Long> getMembers(String groupNo);
}
