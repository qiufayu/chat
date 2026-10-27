package com.fayu.connection.factory;

import com.fayu.connection.server.impl.GroupSessionImpl;
import com.fayu.connection.session.GroupSession;

public class GroupSessionFactory  {
    private static final GroupSession GROUP_SESSION = new GroupSessionImpl();

    public static GroupSession getSession(){
        return GROUP_SESSION;
    }
}
