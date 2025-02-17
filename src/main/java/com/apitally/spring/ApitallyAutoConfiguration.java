package com.apitally.spring;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.SpringVersion;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.apitally.common.ApitallyClient;
import com.apitally.common.dto.Path;

@Configuration
@EnableAsync
@EnableConfigurationProperties(ApitallyProperties.class)
public class ApitallyAutoConfiguration {
    @Bean
    public ApitallyClient apitallyClient(ApitallyProperties properties,
            RequestMappingHandlerMapping requestMappingHandlerMapping) {
        ApitallyClient client = ApitallyClient.getInstance(properties.getClientId(), properties.getEnv());
        List<Path> paths = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(entry -> {
                    RequestMappingInfo mappingInfo = entry.getKey();
                    return mappingInfo.getMethodsCondition().getMethods().stream()
                            .flatMap(method -> {
                                PathPatternsRequestCondition pathPatterns = mappingInfo.getPathPatternsCondition();
                                if (pathPatterns != null && pathPatterns.getPatterns() != null) {
                                    return pathPatterns.getPatterns().stream()
                                            .map(pattern -> new Path(method.name(), pattern.getPatternString()));
                                }
                                PatternsRequestCondition patterns = mappingInfo.getPatternsCondition();
                                if (patterns != null && patterns.getPatterns() != null) {
                                    return patterns.getPatterns().stream()
                                            .map(pattern -> new Path(method.name(), pattern));
                                }
                                return List.<Path>of().stream();
                            });
                })
                .collect(Collectors.toList());
        Map<String, String> versions = Map.of(
                "java", System.getProperty("java.version"),
                "spring", SpringVersion.getVersion(),
                "spring-boot", SpringBootVersion.getVersion());
        client.setStartupData(paths, versions, "java:spring");
        return client;
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
