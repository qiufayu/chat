package com.fayu.common.handler;

import com.fayu.common.exception.CommonException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

/** Shared HTTP error responses for MVC business services. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CommonException.class)
    public ResponseEntity<Map<String, Object>> business(CommonException e) {
        return ResponseEntity.status(e.getCode()).body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请求参数格式或长度不正确"));
    }
}
