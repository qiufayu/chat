package com.fayu.common.config;

import com.fayu.common.utils.RedisIdWorker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CommonAutoConfig {
    @Bean
    public RedisIdWorker redisIdWorker(StringRedisTemplate redisTemplate){
        return new RedisIdWorker(redisTemplate);
    }
}
