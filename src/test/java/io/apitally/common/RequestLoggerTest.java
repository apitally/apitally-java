package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apitally.common.dto.Header;
import io.apitally.common.dto.Request;
import io.apitally.common.dto.Response;

public class RequestLoggerTest {

        private RequestLogger requestLogger;
        private RequestLoggingConfig requestLoggingConfig;

        @BeforeEach
        void setUp() {
                requestLoggingConfig = new RequestLoggingConfig();
                requestLoggingConfig.setEnabled(true);
                requestLoggingConfig.setQueryParamsIncluded(true);
                requestLoggingConfig.setRequestHeadersIncluded(true);
                requestLoggingConfig.setRequestBodyIncluded(true);
                requestLoggingConfig.setResponseHeadersIncluded(true);
                requestLoggingConfig.setResponseBodyIncluded(true);
                requestLogger = new RequestLogger(requestLoggingConfig);
        }

        @AfterEach
        void tearDown() {
                requestLogger.close();
        }

        @Test
        void testEndToEnd() {
                Header[] requestHeaders = new Header[] {
                                new Header("User-Agent", "Test"),
                };
                Header[] responseHeaders = new Header[] {
                                new Header("Content-Type", "application/json"),
                };
                Request request = new Request(
                                System.currentTimeMillis() / 1000.0,
                                "tester",
                                "GET",
                                "/items",
                                "http://test/items",
                                requestHeaders,
                                0L,
                                new byte[0]);
                Response response = new Response(
                                200,
                                0.123,
                                responseHeaders,
                                13L,
                                "{\"items\": []}".getBytes());
                requestLogger.logRequest(request, response);
                requestLogger.maintain();
                requestLogger.rotateFile();

                TempGzipFile logFile = requestLogger.getFile();
                assertNotNull(logFile);
                assertTrue(logFile.getSize() > 0);

                try {
                        List<String> lines = logFile.readDecompressedLines();
                        assertEquals(1, lines.size());

                        // Parse the line as JSON
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(lines.get(0));

                        assertEquals("GET", jsonNode.get("request").get("method").asText());
                        assertEquals("/items", jsonNode.get("request").get("path").asText());
                        assertEquals("http://test/items", jsonNode.get("request").get("url").asText());
                        assertFalse(jsonNode.get("request").has("body"));
                        assertEquals(200, jsonNode.get("response").get("statusCode").asInt());
                        assertEquals(0.123, jsonNode.get("response").get("responseTime").asDouble(), 0.001);
                        assertEquals("{\"items\": []}",
                                        new String(Base64.getDecoder()
                                                        .decode(jsonNode.get("response").get("body").asText())));

                        JsonNode requestHeadersNode = jsonNode.get("request").get("headers");
                        assertTrue(requestHeadersNode.isArray());
                        assertEquals(1, requestHeadersNode.size());
                        assertEquals("User-Agent", requestHeadersNode.get(0).get(0).asText());
                        assertEquals("Test", requestHeadersNode.get(0).get(1).asText());

                        JsonNode responseHeadersNode = jsonNode.get("response").get("headers");
                        assertTrue(responseHeadersNode.isArray());
                        assertEquals(1, responseHeadersNode.size());
                        assertEquals("Content-Type", responseHeadersNode.get(0).get(0).asText());
                        assertEquals("application/json", responseHeadersNode.get(0).get(1).asText());
                } catch (IOException e) {
                        throw new AssertionError("Failed to read gzipped file", e);
                }

                requestLogger.clear();
                requestLogger.maintain();
                requestLogger.rotateFile();

                logFile = requestLogger.getFile();
                assertNull(logFile);
        }

        @Test
        void testExclusion() {
                requestLoggingConfig.setCallbacks(new RequestLoggingCallbacks() {
                        @Override
                        public boolean shouldExclude(Request request, Response response) {
                                return request.getConsumer().contains("tester");
                        }
                });

                // Exclude based on shouldExclude() callback
                Request request = new Request(
                                System.currentTimeMillis() / 1000.0,
                                "tester",
                                "GET",
                                "/items",
                                "http://test/items",
                                new Header[0],
                                0L,
                                new byte[0]);
                Response response = new Response(
                                200,
                                0.123,
                                new Header[0],
                                13L,
                                "{\"items\": []}".getBytes());
                requestLogger.logRequest(request, response);

                // Exclude health check requests
                request = new Request(
                                System.currentTimeMillis() / 1000.0,
                                null,
                                "GET",
                                "/healthz",
                                "http://test/healthz",
                                new Header[0],
                                0L,
                                new byte[0]);
                response = new Response(
                                200,
                                0.123,
                                new Header[0],
                                17L,
                                "{\"healthy\": true}".getBytes());
                requestLogger.logRequest(request, response);

                request = new Request(
                                System.currentTimeMillis() / 1000.0,
                                null,
                                "GET",
                                "/",
                                "http://test/",
                                new Header[] {
                                                new Header("User-Agent", "ELB-HealthChecker/2.0"),
                                },
                                0L,
                                new byte[0]);
                requestLogger.logRequest(request, response);

                requestLogger.maintain();
                requestLogger.rotateFile();
                TempGzipFile logFile = requestLogger.getFile();
                assertNull(logFile);
        }

        @Test
        void testMasking() {
                requestLoggingConfig.setCallbacks(new RequestLoggingCallbacks() {
                        @Override
                        public byte[] maskRequestBody(Request request) {
                                return null;
                        }

                        @Override
                        public byte[] maskResponseBody(Request request, Response response) {
                                return null;
                        }
                });

                Header[] requestHeaders = new Header[] {
                                new Header("Authorization", "Bearer 1234567890"),
                                new Header("Content-Type", "application/json"),
                };
                Header[] responseHeaders = new Header[] {
                                new Header("Content-Type", "application/json"),
                };
                Request request = new Request(
                                System.currentTimeMillis() / 1000.0,
                                "tester",
                                "POST",
                                "/items",
                                "http://test/items?token=my-secret-token",
                                requestHeaders,
                                16L,
                                "{\"key\": \"value\"}".getBytes());
                Response response = new Response(
                                200,
                                0.123,
                                responseHeaders,
                                16L,
                                "{\"key\": \"value\"}".getBytes());
                requestLogger.logRequest(request, response);

                requestLogger.maintain();
                requestLogger.rotateFile();
                TempGzipFile logFile = requestLogger.getFile();
                assertNotNull(logFile);
                assertTrue(logFile.getSize() > 0);

                try {
                        List<String> lines = logFile.readDecompressedLines();
                        assertEquals(1, lines.size());

                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(lines.get(0));

                        assertEquals("http://test/items?token=******", jsonNode.get("request").get("url").asText());
                        assertEquals("<masked>",
                                        new String(Base64.getDecoder()
                                                        .decode(jsonNode.get("response").get("body").asText())));

                        JsonNode requestHeadersNode = jsonNode.get("request").get("headers");
                        assertTrue(requestHeadersNode.isArray());
                        assertEquals(2, requestHeadersNode.size());
                        assertEquals("Authorization", requestHeadersNode.get(0).get(0).asText());
                        assertEquals("******", requestHeadersNode.get(0).get(1).asText());
                } catch (IOException e) {
                        throw new AssertionError("Failed to read gzipped file", e);
                }
        }
}
