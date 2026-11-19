package com.fayu.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.fayu.message.mapper")
@SpringBootApplication
@EnableRetry
@EnableRabbit
@EnableScheduling
public class ChatMessageApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatMessageApplication.class,args);
    }
}