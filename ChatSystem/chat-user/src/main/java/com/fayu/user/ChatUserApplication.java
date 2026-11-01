package com.fayu.user;

import com.fayu.api.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.fayu.user.mepper")
@SpringBootApplication
@EnableFeignClients(basePackages = "com.fayu.api.client",defaultConfiguration = DefaultFeignConfig.class)
public class ChatUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatUserApplication.class,args);
    }
}