package com.fayu.connection.factory;

import com.fayu.connection.server.impl.SingleSessionImpl;
import com.fayu.connection.session.SingleSession;

public class SingleSessionFactory{
    private static final SingleSession SINGLE_SESSION = new SingleSessionImpl();
    public static SingleSession getSession(){
        return SINGLE_SESSION;
    }
}
