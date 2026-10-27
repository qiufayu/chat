package com.fayu.group;

import com.fayu.api.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.fayu.api.client",defaultConfiguration = DefaultFeignConfig.class)
@MapperScan("com.fayu.group.mapper")
@SpringBootApplication
public class ChatGroupApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatGroupApplication.class, args);
    }
}