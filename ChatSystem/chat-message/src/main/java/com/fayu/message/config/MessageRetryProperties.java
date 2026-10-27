package com.fayu.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "chat.msg.retry")
public class MessageRetryProperties {
    private Integer maxRetryCount;
    private Integer multiplier;
    private Duration initialInterval;
}
