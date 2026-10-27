package com.fayu.common.exception;

public class ESRequesException extends CommonException {
    public ESRequesException(String message) {
        super(message, 500);
    }
}
