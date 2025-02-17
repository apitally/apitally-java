package com.apitally.spring;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableAsync;

import com.apitally.common.ApitallyClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties(ApitallyProperties.class)
public class ApitallyAutoConfiguration {
    @Bean
    public ApitallyClient apitallyClient(ApitallyProperties properties) {
        return ApitallyClient.getInstance(properties.getClientId(), properties.getEnvironment());
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
