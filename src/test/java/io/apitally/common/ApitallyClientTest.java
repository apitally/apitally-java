package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.apitally.common.dto.Header;
import io.apitally.common.dto.Path;
import io.apitally.common.dto.Request;
import io.apitally.common.dto.Response;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApitallyClientTest {

    private ApitallyClient client;
    private ApitallyClient clientSpy;

    @BeforeEach
    void setUp() {
        RequestLoggingConfig requestLoggingConfig = new RequestLoggingConfig();
        requestLoggingConfig.setEnabled(true);
        client = new ApitallyClient("00000000-0000-0000-0000-000000000000", "test", requestLoggingConfig);
        clientSpy = spy(client);
        when(clientSpy.sendHubRequest(any(HttpRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ApitallyClient.HubRequestStatus.OK));
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    @Test
    void testSync() {
        Header[] requestHeaders = new Header[] {
            new Header("Content-Type", "application/json"), new Header("User-Agent", "test-client"),
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
        Response response = new Response(200, 0.123, responseHeaders, 13L, "{\"items\": []}".getBytes());
        client.requestLogger.logRequest(request, response, null, null, null, null);
        client.requestLogger.maintain();

        List<Path> paths = Arrays.asList(new Path("GET", "/items"));
        Map<String, String> versions = new HashMap<>();
        versions.put("package", "1.0.0");
        clientSpy.setStartupData(paths, versions, "java:test");
        clientSpy.startSync();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(clientSpy, timeout(5000).times(3)).sendHubRequest(requestCaptor.capture());
        List<HttpRequest> capturedRequests = requestCaptor.getAllValues();
        assertTrue(capturedRequests.stream().anyMatch(r -> r.uri().toString().contains("/startup")));
        assertTrue(capturedRequests.stream().anyMatch(r -> r.uri().toString().contains("/sync")));
        assertTrue(capturedRequests.stream().anyMatch(r -> r.uri().toString().contains("/log")));

        clientSpy.stopSync();
    }
}
