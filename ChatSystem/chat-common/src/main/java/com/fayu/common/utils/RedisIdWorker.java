package com.fayu.common.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
public class RedisIdWorker {

    private final StringRedisTemplate redisTemplate;

    private static final long INITIAL_VALUE = 1000000000L;


    public String generateId(String keyPrefix){
        redisTemplate.opsForValue().setIfAbsent(keyPrefix,String.valueOf(INITIAL_VALUE));
        Long id = redisTemplate.opsForValue().increment(keyPrefix);
        return String.valueOf(id);
    }
    public String generateId(String keyPrefix,Long initValue){
        redisTemplate.opsForValue().setIfAbsent(keyPrefix,String.valueOf(initValue));
        Long id = redisTemplate.opsForValue().increment(keyPrefix);
        return String.valueOf(id);
    }

    public Long msgSeqIncrease(String keyPrefix){
        redisTemplate.opsForValue().setIfAbsent(keyPrefix,String.valueOf(0));
        return redisTemplate.opsForValue().increment(keyPrefix);
    }
}
