package com.fayu.connection;

import com.fayu.api.client.MsgClient;
import com.fayu.api.client.UserClient;
import com.fayu.api.config.DefaultFeignConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableFeignClients(basePackages = "com.fayu.api.client",defaultConfiguration = DefaultFeignConfig.class)
@SpringBootApplication
@EnableScheduling
@RequiredArgsConstructor
@EnableRabbit
public class ChatConnectionApplication implements ApplicationRunner {

    private final MsgClient msgClient;
    private final UserClient userClient;

    public static void main(String[] args) {
        SpringApplication.run(ChatConnectionApplication.class,args);
    }
    // 容器启动完成后，Spring 会自动执行这个方法
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始预热 Feign 客户端...");
        try {
            msgClient.start();
            userClient.start();
            log.info("预热 Nacos 服务完成.....");
        } catch (Exception e) {
            log.error("预热 Feign 客户端失败，但服务继续启动", e);
        }
    }
}