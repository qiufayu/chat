package com.fayu.connection.executor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

@Component
public class ChatBusinessExecutor extends ThreadPoolTaskExecutor {
    public ChatBusinessExecutor(){
        this.setCorePoolSize(32);
        this.setMaxPoolSize(150);
        this.setQueueCapacity(1000);
        this.setThreadNamePrefix("chat-biz-");
        this.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //优雅停机
        // 服务关闭时，等待正在执行的任务完成，避免消息处理一半中断。
        this.setWaitForTasksToCompleteOnShutdown(true);
        this.setAwaitTerminationSeconds(30);

        // 初始化
        this.initialize();
    }
}
