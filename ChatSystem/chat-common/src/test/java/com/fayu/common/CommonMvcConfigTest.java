package com.fayu.common;

import com.fayu.common.config.MvcConfig;
import com.fayu.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommonMvcConfigTest {
    @Test
    void mvcServicesReceiveTheSharedHandlerWithoutScanningTheCommonPackage() {
        new WebApplicationContextRunner().withUserConfiguration(MvcConfig.class).run(context ->
                assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void nonServletServicesDoNotLoadMvcConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(MvcConfig.class).run(context ->
                assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class).doesNotHaveBean(MvcConfig.class));
    }
}
