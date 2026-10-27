package com.fayu.common.exception;

public class BaseException extends CommonException {

    public BaseException(String message, int code) {
        super(message, code);
    }

    public BaseException(String message, Throwable cause, int code) {
        super(message, cause, code);
    }

    public BaseException(Throwable cause, int code) {
        super(cause, code);
    }
}
