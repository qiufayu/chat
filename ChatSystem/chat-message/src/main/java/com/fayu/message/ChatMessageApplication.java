package com.fayu.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@MapperScan("com.fayu.message.mapper")
@SpringBootApplication
@EnableRetry
public class ChatMessageApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatMessageApplication.class,args);
    }
}