package com.fayu.connection;

import com.fayu.api.config.DefaultFeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients(basePackages = "com.fayu.api.client",defaultConfiguration = DefaultFeignConfig.class)
@SpringBootApplication
@EnableScheduling
public class ChatConnectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatConnectionApplication.class,args);
    }
}