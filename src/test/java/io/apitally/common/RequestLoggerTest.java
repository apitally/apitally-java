package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        Exception exception = new Exception("test");
        requestLogger.logRequest(request, response, exception);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(1, items.length);

        JsonNode jsonNode = items[0];
        assertEquals("GET", jsonNode.get("request").get("method").asText());
        assertEquals("/items", jsonNode.get("request").get("path").asText());
        assertEquals("http://test/items", jsonNode.get("request").get("url").asText());
        assertFalse(jsonNode.get("request").has("body"));
        assertEquals(200, jsonNode.get("response").get("statusCode").asInt());
        assertEquals(0.123, jsonNode.get("response").get("responseTime").asDouble(), 0.001);
        assertEquals("{\"items\":[]}",
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

        JsonNode exceptionNode = jsonNode.get("exception");
        assertNotNull(exceptionNode);
        assertEquals("Exception", exceptionNode.get("type").asText());
        assertEquals("test", exceptionNode.get("message").asText());
        assertTrue(exceptionNode.get("stackTrace").asText().contains("test"));

        requestLogger.clear();

        items = getLoggedItems(requestLogger);
        assertEquals(0, items.length);
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
}
