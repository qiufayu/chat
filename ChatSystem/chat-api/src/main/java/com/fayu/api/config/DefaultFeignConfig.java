package com.fayu.api.config;

import com.fayu.common.utils.UserContext;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DefaultFeignConfig {

    @Bean
    public Logger.Level feignLogLevel(){
        return Logger.Level.FULL;
    }

    @Bean
    public RequestInterceptor userInfoRequestInterceptor(){
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate requestTemplate) {
                // A caller may explicitly supply the authenticated channel identity from an executor thread.
                if (requestTemplate.headers().containsKey("user-info")) {
                    return;
                }
                Long userId = UserContext.getUser();
                log.debug("请求 path={} 添加信息 user-info={}",requestTemplate.url(),userId);
                if (userId == null){
                    return;
                }
                // 不可以先获取到requestTemplate.request()再去添加请求头
                requestTemplate.header("user-info",userId.toString());
            }
        };
    }
}
