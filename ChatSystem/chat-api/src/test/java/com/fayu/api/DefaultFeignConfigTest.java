package com.fayu.api;

import com.fayu.api.config.DefaultFeignConfig;
import com.fayu.common.utils.UserContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DefaultFeignConfigTest {
    @AfterEach
    void clearIdentity() { UserContext.removeUser(); }

    @Test
    void explicitChannelIdentityIsNotOverwrittenByThreadContext() {
        UserContext.setUser(999L);
        RequestTemplate template = new RequestTemplate();
        template.header("user-info", "1");
        new DefaultFeignConfig().userInfoRequestInterceptor().apply(template);
        assertEquals(List.of("1"), List.copyOf(template.headers().get("user-info")));
    }

    @Test
    void missingThreadIdentityDoesNotCreateAnAuthenticationHeader() {
        RequestTemplate template = new RequestTemplate();
        new DefaultFeignConfig().userInfoRequestInterceptor().apply(template);
        assertFalse(template.headers().containsKey("user-info"));
    }
}
