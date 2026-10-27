package com.fayu.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.fayu.user.mepper")
@SpringBootApplication
public class ChatUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatUserApplication.class,args);
    }
}