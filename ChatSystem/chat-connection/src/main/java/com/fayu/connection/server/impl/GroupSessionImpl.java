package com.fayu.connection.server.impl;

import com.fayu.connection.domain.po.Group;
import com.fayu.connection.session.GroupSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroupSessionImpl implements GroupSession {
    public static Map<String, Group> groupMap  = new ConcurrentHashMap<>();


    @Override
    public Group cretaeGroup(String groupNo, Set<Long> members) {
        Group group = new Group(groupNo, members);
        Group absent = groupMap.putIfAbsent(groupNo, group);
        return absent == null ? group : null;
    }

    @Override
    public Group joinGroup(String groupNo, Long memberId) {
        return groupMap.computeIfPresent(groupNo,(k,g)->{
            g.getMembers().add(memberId);
            return g;
        });
    }

    @Override
    public Group removeMember(String groupNo, Long memberId) {
        return groupMap.computeIfPresent(groupNo,(k,g)->{
            g.getMembers().remove(memberId);
            return g;
        });
    }

    @Override
    public Group removeGroup(String groupNo) {
        return groupMap.remove(groupNo);
    }

    @Override
    public Set<Long> getMembers(String groupNo) {
        return groupMap.get(groupNo).getMembers();
    }

}
