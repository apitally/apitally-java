package com.apitally.spring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import com.apitally.common.ApitallyClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties(ApitallyProperties.class)
public class ApitallyAutoConfiguration {
    @Bean
    @ConditionalOnProperty(name = "apitally.client-id")
    public ApitallyClient apitallyClient(ApitallyProperties properties) {
        return new ApitallyClient(properties.getClientId(), properties.getEnvironment());
    }

    @Bean
    public FilterRegistrationBean<ApitallyFilter> filterRegistration(ApitallyProperties properties,
            ApitallyClient apitallyClient) {
        final FilterRegistrationBean<ApitallyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ApitallyFilter(apitallyClient));
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE - 10); // Similar to HttpTraceFilter
        return registrationBean;
    }
}
