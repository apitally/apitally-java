package io.apitally.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apitally.common.ApitallyClient;
import io.apitally.common.LogAppender;
import io.apitally.common.RequestLogger;
import io.apitally.common.TempGzipFile;
import io.apitally.common.dto.Consumer;
import io.apitally.common.dto.Path;
import io.apitally.common.dto.Requests;
import io.apitally.common.dto.ServerErrors;
import io.apitally.common.dto.ValidationErrors;
import io.apitally.spring.app.TestApplication;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {ApitallyFilterTest.TestConfig.class})
class ApitallyFilterTest {

    private static final Logger logger = LoggerFactory.getLogger(ApitallyFilterTest.class);

    @TestConfiguration
    @EnableConfigurationProperties(ApitallyProperties.class)
    static class TestConfig {
        @Bean
        public ApitallyClient apitallyClient(ApitallyProperties properties) {
            LogAppender.register();
            ApitallyClient client =
                    new ApitallyClient(properties.getClientId(), properties.getEnv(), properties.getRequestLogging());
            return client;
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
        apitallyClient.validationErrorCounter.getAndResetValidationErrors();
        apitallyClient.serverErrorCounter.getAndResetServerErrors();
        apitallyClient.consumerRegistry.reset();
        apitallyClient.requestLogger.getConfig().setEnabled(true);
    }

    @Test
    void testRequestCounter() {
        apitallyClient.requestLogger.getConfig().setEnabled(false);
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

        delay(100);

        List<Requests> requests = apitallyClient.requestCounter.getAndResetRequests();
        assertEquals(4, requests.size(), "4 requests counted");
        assertTrue(requests.stream()
                .anyMatch(r -> r.getMethod().equals("GET")
                        && r.getPath().equals("/items")
                        && r.getStatusCode() == 200
                        && r.getRequestCount() == 1
                        && r.getResponseSizeSum() > 0));
        assertTrue(requests.stream()
                .anyMatch(r -> r.getMethod().equals("GET")
                        && r.getPath().equals("/items/{id}")
                        && r.getStatusCode() == 200
                        && r.getRequestCount() == 2));
        assertTrue(requests.stream()
                .anyMatch(r -> r.getMethod().equals("GET")
                        && r.getPath().equals("/items/{id}")
                        && r.getStatusCode() == 400
                        && r.getRequestCount() == 1));
        assertTrue(requests.stream()
                .anyMatch(r -> r.getMethod().equals("GET")
                        && r.getPath().equals("/throw")
                        && r.getStatusCode() == 500
                        && r.getRequestCount() == 1
                        && r.getResponseSizeSum() == 0));

        requests = apitallyClient.requestCounter.getAndResetRequests();
        assertEquals(0, requests.size(), "No requests counted after reset");
    }

    @Test
    void testValidationErrorCounter() {
        ResponseEntity<String> response;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"name\": \"x\"}", headers);
        response = restTemplate.postForEntity("/items", request, String.class);
        assertTrue(response.getStatusCode().is4xxClientError());

        response = restTemplate.getForEntity("/items?name=x", String.class);
        assertTrue(response.getStatusCode().is4xxClientError());

        response = restTemplate.getForEntity("/items/0", String.class);
        assertTrue(response.getStatusCode().is4xxClientError());

        delay(100);

        List<ValidationErrors> validationErrors = apitallyClient.validationErrorCounter.getAndResetValidationErrors();
        assertEquals(3, validationErrors.size());
        assertTrue(validationErrors.stream()
                .anyMatch(e -> e.getMethod().equals("POST")
                        && e.getPath().equals("/items")
                        && e.getLoc().equals("testItem.name")
                        && e.getType().equals("Size")
                        && e.getErrorCount() == 1));
        assertTrue(validationErrors.stream()
                .anyMatch(e -> e.getMethod().equals("GET")
                        && e.getPath().equals("/items")
                        && e.getLoc().equals("getItems.name")
                        && e.getType().equals("Size")
                        && e.getErrorCount() == 1));
        assertTrue(validationErrors.stream()
                .anyMatch(e -> e.getMethod().equals("GET")
                        && e.getPath().equals("/items/{id}")
                        && e.getLoc().equals("getItem.id")
                        && e.getType().equals("Min")
                        && e.getErrorCount() == 1));

        validationErrors = apitallyClient.validationErrorCounter.getAndResetValidationErrors();
        assertEquals(0, validationErrors.size());
    }

    @Test
    void testServerErrorCounter() {
        ResponseEntity<String> response = restTemplate.getForEntity("/throw", String.class);
        assertTrue(response.getStatusCode().is5xxServerError());

        delay(100);

        List<ServerErrors> serverErrors = apitallyClient.serverErrorCounter.getAndResetServerErrors();
        assertEquals(1, serverErrors.size());
        assertTrue(serverErrors.stream()
                .anyMatch(e -> e.getType().equals("TestException")
                        && e.getMessage().equals("test")
                        && e.getStackTraceString().length() > 100
                        && e.getErrorCount() == 1));

        serverErrors = apitallyClient.serverErrorCounter.getAndResetServerErrors();
        assertEquals(0, serverErrors.size());
    }

    @Test
    void testConsumerRegistry() {
        restTemplate.getForEntity("/items", String.class);
        restTemplate.getForEntity("/items", String.class);

        delay(100);

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
        assertEquals(6, paths.size());
        assertTrue(paths.stream()
                .anyMatch(p -> p.getMethod().equals("GET") && p.getPath().equals("/items")));
        assertTrue(paths.stream()
                .anyMatch(p -> p.getMethod().equals("GET") && p.getPath().equals("/items/{id}")));
        assertTrue(paths.stream()
                .anyMatch(p -> p.getMethod().equals("POST") && p.getPath().equals("/items")));
        assertTrue(paths.stream()
                .anyMatch(p -> p.getMethod().equals("PUT") && p.getPath().equals("/items/{id}")));
        assertTrue(paths.stream()
                .anyMatch(p -> p.getMethod().equals("DELETE") && p.getPath().equals("/items/{id}")));
    }

    @Test
    void testGetVersions() {
        Map<String, String> versions = ApitallyUtils.getVersions();
        logger.info("Versions: {}", versions);
        assertEquals(3, versions.size());
    }

    @Test
    void testRequestLogger() {
        apitallyClient.requestLogger.getConfig().setEnabled(true);
        apitallyClient.requestLogger.getConfig().setRequestBodyIncluded(true);
        apitallyClient.requestLogger.getConfig().setResponseBodyIncluded(true);
        apitallyClient.requestLogger.getConfig().setLogCaptureEnabled(true);
        apitallyClient.requestLogger.clear();

        ResponseEntity<String> response = restTemplate.getForEntity("/items", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"id\": 1, \"name\": \"bob\"}", headers);
        response = restTemplate.postForEntity("/items", request, String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        delay(100);

        JsonNode[] items = getLoggedItems(apitallyClient.requestLogger);
        assertEquals(2, items.length);

        // Verify GET request logging
        JsonNode firstItem = items[0];
        assertEquals("GET", firstItem.get("request").get("method").asText());
        assertTrue(firstItem.get("request").get("url").asText().contains("/items"));
        assertEquals(200, firstItem.get("response").get("statusCode").asInt());
        String responseBody = new String(
                Base64.getDecoder().decode(firstItem.get("response").get("body").asText()));
        assertTrue(responseBody.contains("alice"));

        // Verify application logs were captured
        assertTrue(firstItem.has("logs"));
        assertTrue(firstItem.get("logs").isArray());
        assertTrue(firstItem.get("logs").size() > 0);
        assertTrue(firstItem.get("logs").get(0).get("message").asText().contains("Getting items"));

        // Verify POST request logging with request body
        JsonNode secondItem = items[1];
        assertEquals("POST", secondItem.get("request").get("method").asText());
        assertTrue(secondItem.get("request").get("url").asText().contains("/items"));
        String requestBody = new String(
                Base64.getDecoder().decode(secondItem.get("request").get("body").asText()));
        assertTrue(requestBody.contains("bob"));
    }

    private JsonNode[] getLoggedItems(RequestLogger requestLogger) {
        requestLogger.maintain();
        requestLogger.rotateFile();

        TempGzipFile logFile = requestLogger.getFile();
        if (logFile == null) {
            return new JsonNode[0];
        }

        try {
            List<String> lines = logFile.readDecompressedLines();
            JsonNode[] items = new JsonNode[lines.size()];
            ObjectMapper objectMapper = new ObjectMapper();

            for (int i = 0; i < lines.size(); i++) {
                items[i] = objectMapper.readTree(lines.get(i));
            }

            return items;
        } catch (IOException e) {
            throw new AssertionError("Failed to read gzipped file", e);
        }
    }

    private void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
