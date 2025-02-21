package com.apitally.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.apitally.common.ApitallyClient;
import com.apitally.common.dto.Consumer;
import com.apitally.common.dto.Path;
import com.apitally.common.dto.Requests;
import com.apitally.common.dto.ServerErrors;
import com.apitally.spring.app.TestApplication;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = { ApitallyFilterTest.TestConfig.class })
class ApitallyFilterTest {

    @TestConfiguration
    @EnableConfigurationProperties(ApitallyProperties.class)
    static class TestConfig {
        @Bean
        public ApitallyClient apitallyClient(ApitallyProperties properties) {
            return ApitallyClient.getInstance(properties.getClientId(), properties.getEnv(),
                    properties.getRequestLogging());
        }

        @Bean
        public FilterRegistrationBean<ApitallyFilter> filterRegistration(ApitallyClient apitallyClient) {
            final FilterRegistrationBean<ApitallyFilter> registrationBean = new FilterRegistrationBean<>();
            registrationBean.setFilter(new ApitallyFilter(apitallyClient));
            registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
            return registrationBean;
        }

        @Bean
        public ApitallyExceptionResolver apitallyExceptionResolver() {
            return new ApitallyExceptionResolver();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApitallyClient apitallyClient;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @BeforeEach
    void setUp() {
        apitallyClient.requestCounter.getAndResetRequests();
        apitallyClient.serverErrorCounter.getAndResetServerErrors();
        apitallyClient.consumerRegistry.getAndResetConsumers();
    }

    @Test
    void testRequestCounter() {
        ResponseEntity<String> response;

        response = restTemplate.getForEntity("/items", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = restTemplate.getForEntity("/items/0", String.class);
        assertTrue(response.getStatusCode().is4xxClientError());

        response = restTemplate.getForEntity("/items/1", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = restTemplate.getForEntity("/items/2", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = restTemplate.getForEntity("/throw", String.class);
        assertTrue(response.getStatusCode().is5xxServerError());

        List<Requests> requests = apitallyClient.requestCounter.getAndResetRequests();
        assertEquals(4, requests.size());
        assertTrue(requests.stream()
                .anyMatch(r -> r.getMethod().equals("GET") && r.getPath().equals("/items") && r.getStatusCode() == 200
                        && r.getRequestCount() == 1));
        assertTrue(requests.stream().anyMatch(
                r -> r.getPath().equals("/items/{id}") && r.getStatusCode() == 200 && r.getRequestCount() == 2));
        assertTrue(requests.stream().anyMatch(
                r -> r.getPath().equals("/items/{id}") && r.getStatusCode() == 400 && r.getRequestCount() == 1));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals("/throw") && r.getStatusCode() == 500));

        requests = apitallyClient.requestCounter.getAndResetRequests();
        assertEquals(0, requests.size());
    }

    @Test
    void testServerErrorCounter() {
        ResponseEntity<String> response = restTemplate.getForEntity("/throw", String.class);
        assertTrue(response.getStatusCode().is5xxServerError());

        List<ServerErrors> serverErrors = apitallyClient.serverErrorCounter.getAndResetServerErrors();
        assertEquals(1, serverErrors.size());
        assertTrue(serverErrors.stream().anyMatch(e -> e.getType().equals("TestException")
                && e.getMessage().equals("test") && e.getStackTraceString().length() > 100 && e.getErrorCount() == 1));

        serverErrors = apitallyClient.serverErrorCounter.getAndResetServerErrors();
        assertEquals(0, serverErrors.size());
    }

    @Test
    void testConsumerRegistry() {
        restTemplate.getForEntity("/items", String.class);
        restTemplate.getForEntity("/items", String.class);

        List<Consumer> consumers = apitallyClient.consumerRegistry.getAndResetConsumers();
        assertEquals(1, consumers.size());
        Consumer consumer = consumers.get(0);
        assertEquals("tester", consumer.getIdentifier());
        assertEquals("Tester", consumer.getName());
        assertEquals("Test Group", consumer.getGroup());

        consumers = apitallyClient.consumerRegistry.getAndResetConsumers();
        assertEquals(0, consumers.size());
    }

    @Test
    void testGetPaths() {
        List<Path> paths = ApitallyUtils.getPaths(requestMappingHandlerMapping);
        assertEquals(7, paths.size());
        assertTrue(paths.stream().anyMatch(p -> p.getMethod().equals("GET") && p.getPath().equals("/items")));
        assertTrue(paths.stream().anyMatch(p -> p.getMethod().equals("GET") && p.getPath().equals("/items/{id}")));
        assertTrue(paths.stream().anyMatch(p -> p.getMethod().equals("POST") && p.getPath().equals("/items")));
        assertTrue(paths.stream().anyMatch(p -> p.getMethod().equals("PUT") && p.getPath().equals("/items/{id}")));
        assertTrue(paths.stream().anyMatch(p -> p.getMethod().equals("DELETE") && p.getPath().equals("/items/{id}")));
    }

    @Test
    void testGetVersions() {
        Map<String, String> versions = ApitallyUtils.getVersions();
        assertEquals(3, versions.size());
    }
}
