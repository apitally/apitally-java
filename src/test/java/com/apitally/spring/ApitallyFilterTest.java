package com.apitally.spring;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.apitally.common.ApitallyClient;
import com.apitally.common.dto.Path;
import com.apitally.spring.app.TestApplication;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(classes = ApitallyFilterTest.TestConfig.class)
class ApitallyFilterTest {

    @TestConfiguration
    @EnableConfigurationProperties(ApitallyProperties.class)
    static class TestConfig {
        @Bean
        public ApitallyClient apitallyClient(ApitallyProperties properties,
                RequestMappingHandlerMapping requestMappingHandlerMapping) {
            ApitallyClient client = ApitallyClient.getInstance(properties.getClientId(), properties.getEnv(),
                    properties.getRequestLogging());
            List<Path> paths = ApitallyUtils.getPaths(requestMappingHandlerMapping);
            Map<String, String> versions = ApitallyUtils.getVersions();
            client.setStartupData(paths, versions, "java:spring");
            return client;
        }

        @Bean
        public FilterRegistrationBean<ApitallyFilter> filterRegistration(ApitallyClient apitallyClient) {
            final FilterRegistrationBean<ApitallyFilter> registrationBean = new FilterRegistrationBean<>();
            registrationBean.setFilter(new ApitallyFilter(apitallyClient));
            registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
            return registrationBean;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApitallyClient apitallyClient;

    @Test
    void testHealthEndpoint() throws Exception {
        System.out.println("apitallyClient: " + apitallyClient.toString());
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk());
    }

    @Test
    void testEmployeesEndpoint() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk());
    }
}
