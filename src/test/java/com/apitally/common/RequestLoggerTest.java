package com.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.apitally.common.dto.Header;
import com.apitally.common.dto.Request;
import com.apitally.common.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RequestLoggerTest {

    private RequestLogger requestLogger;

    @BeforeEach
    void setUp() {
        RequestLoggingConfig requestLoggingConfig = new RequestLoggingConfig();
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
                new Header("Authorization", "Bearer 1234567890"),
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

        // Read the gzipped file content
        try (InputStream inputStream = logFile.getInputStream();
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzipInputStream))) {

            // Read lines and verify there is exactly one
            List<String> lines = reader.lines().collect(Collectors.toList());
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
                    new String(Base64.getDecoder().decode(jsonNode.get("response").get("body").asText())));

            JsonNode requestHeadersNode = jsonNode.get("request").get("headers");
            assertTrue(requestHeadersNode.isArray());
            assertEquals(2, requestHeadersNode.size());
            assertEquals("Authorization", requestHeadersNode.get(0).get(0).asText());
            assertEquals("******", requestHeadersNode.get(0).get(1).asText());
            assertEquals("User-Agent", requestHeadersNode.get(1).get(0).asText());
            assertEquals("Test", requestHeadersNode.get(1).get(1).asText());

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
}
