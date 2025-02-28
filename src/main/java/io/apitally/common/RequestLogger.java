package io.apitally.common;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.apitally.common.dto.Header;
import io.apitally.common.dto.Request;
import io.apitally.common.dto.Response;

public class RequestLogger {
    private static final Logger logger = LoggerFactory.getLogger(RequestLogger.class);

    private static final int MAX_BODY_SIZE = 50_000; // 50 KB (uncompressed)
    private static final int MAX_FILE_SIZE = 1_000_000; // 1 MB (compressed)
    private static final int MAX_FILES = 50;
    private static final int MAX_PENDING_WRITES = 100;
    private static final byte[] BODY_TOO_LARGE = "<body too large>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY_MASKED = "<masked>".getBytes(StandardCharsets.UTF_8);
    private static final String MASKED = "******";
    public static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("application/json", "text/plain");
    private static final List<String> EXCLUDE_PATH_PATTERNS = Arrays.asList(
            "/_?healthz?$",
            "/_?health[_-]?checks?$",
            "/_?heart[_-]?beats?$",
            "/ping$",
            "/ready$",
            "/live$");
    private static final List<String> EXCLUDE_USER_AGENT_PATTERNS = Arrays.asList(
            "health[-_ ]?check",
            "microsoft-azure-application-lb",
            "googlehc",
            "kube-probe");
    private static final List<String> MASK_QUERY_PARAM_PATTERNS = Arrays.asList(
            "auth",
            "api-?key",
            "secret",
            "token",
            "password",
            "pwd");
    private static final List<String> MASK_HEADER_PATTERNS = Arrays.asList(
            "auth",
            "api-?key",
            "secret",
            "token",
            "cookie");
    private static final int MAINTAIN_INTERVAL_SECONDS = 1;

    private final RequestLoggingConfig config;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock;
    private final Deque<String> pendingWrites;
    private final Deque<TempGzipFile> files;
    private TempGzipFile currentFile;
    private boolean enabled;
    private Long suspendUntil;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> maintainTask;

    private final List<Pattern> compiledPathExcludePatterns;
    private final List<Pattern> compiledUserAgentExcludePatterns;
    private final List<Pattern> compiledQueryParamMaskPatterns;
    private final List<Pattern> compiledHeaderMaskPatterns;

    public RequestLogger(RequestLoggingConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.lock = new ReentrantLock();
        this.pendingWrites = new ConcurrentLinkedDeque<>();
        this.files = new ConcurrentLinkedDeque<>();
        this.enabled = config.isEnabled();

        this.compiledPathExcludePatterns = compilePatterns(EXCLUDE_PATH_PATTERNS, config.getPathExcludePatterns());
        this.compiledUserAgentExcludePatterns = compilePatterns(EXCLUDE_USER_AGENT_PATTERNS, null);
        this.compiledQueryParamMaskPatterns = compilePatterns(MASK_QUERY_PARAM_PATTERNS,
                config.getQueryParamMaskPatterns());
        this.compiledHeaderMaskPatterns = compilePatterns(MASK_HEADER_PATTERNS, config.getHeaderMaskPatterns());

        if (enabled) {
            startMaintenance();
        }
    }

    private static List<Pattern> compilePatterns(List<String> defaultPatterns, List<String> additionalPatterns) {
        List<String> patterns = new ArrayList<>(defaultPatterns);
        if (additionalPatterns != null) {
            patterns.addAll(additionalPatterns);
        }
        return patterns.stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());
    }

    public RequestLoggingConfig getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSuspendUntil(long timestamp) {
        this.suspendUntil = timestamp;
    }

    public void logRequest(Request request, Response response) {
        if (!enabled || suspendUntil != null && suspendUntil > System.currentTimeMillis()) {
            return;
        }

        try {
            String userAgent = findHeader(request.getHeaders(), "user-agent");
            if (shouldExcludePath(request.getPath()) || shouldExcludeUserAgent(userAgent)
                    || (config.getCallbacks() != null && config.getCallbacks().shouldExclude(request, response))) {
                return;
            }

            // Process query params and URL
            if (request.getUrl() != null) {
                try {
                    URL url = new URL(request.getUrl());
                    String query = url.getQuery();
                    if (!config.isQueryParamsIncluded()) {
                        query = null;
                    } else if (query != null) {
                        query = maskQueryParams(query);
                    }
                    request.setUrl(new java.net.URL(url.getProtocol(), url.getHost(), url.getPort(),
                            url.getPath() + (query != null ? "?" + query : "")).toString());
                } catch (MalformedURLException e) {
                    return;
                }
            }

            // Process headers
            request.setHeaders(
                    config.isRequestHeadersIncluded()
                            ? maskHeaders(request.getHeaders()).toArray(new Header[0])
                            : new Header[0]);
            response.setHeaders(
                    config.isResponseHeadersIncluded()
                            ? maskHeaders(response.getHeaders()).toArray(new Header[0])
                            : new Header[0]);

            // Process request body
            if (!config.isRequestBodyIncluded() || !hasSupportedContentType(request.getHeaders())) {
                request.setBody(null);
            } else if (request.getBody() != null) {
                if (request.getBody().length > MAX_BODY_SIZE) {
                    request.setBody(BODY_TOO_LARGE);
                } else if (config.getCallbacks() != null) {
                    byte[] maskedBody = config.getCallbacks().maskRequestBody(request);
                    request.setBody(maskedBody != null ? maskedBody : BODY_MASKED);
                    if (request.getBody().length > MAX_BODY_SIZE) {
                        request.setBody(BODY_TOO_LARGE);
                    }
                }
            }

            // Process response body
            if (!config.isResponseBodyIncluded() || !hasSupportedContentType(response.getHeaders())) {
                response.setBody(null);
            } else if (response.getBody() != null) {
                if (response.getBody().length > MAX_BODY_SIZE) {
                    response.setBody(BODY_TOO_LARGE);
                } else if (config.getCallbacks() != null) {
                    byte[] maskedBody = config.getCallbacks().maskResponseBody(request, response);
                    response.setBody(maskedBody != null ? maskedBody : BODY_MASKED);
                    if (response.getBody().length > MAX_BODY_SIZE) {
                        response.setBody(BODY_TOO_LARGE);
                    }
                }
            }

            // Create log item
            ObjectNode item = objectMapper.createObjectNode();
            item.put("uuid", UUID.randomUUID().toString());
            item.set("request", skipEmptyValues(objectMapper.valueToTree(request)));
            item.set("response", skipEmptyValues(objectMapper.valueToTree(response)));

            String serializedItem = objectMapper.writeValueAsString(item);
            pendingWrites.add(serializedItem);

            if (pendingWrites.size() > MAX_PENDING_WRITES) {
                pendingWrites.poll();
            }
        } catch (Exception e) {
            logger.error("Error while logging request", e);
        }
    }

    public void writeToFile() throws IOException {
        if (!enabled || pendingWrites.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            if (currentFile == null) {
                currentFile = new TempGzipFile();
            }
            String item;
            while ((item = pendingWrites.poll()) != null) {
                currentFile.writeLine(item.getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            lock.unlock();
        }
    }

    public TempGzipFile getFile() {
        return files.poll();
    }

    public void retryFileLater(TempGzipFile file) {
        files.addFirst(file);
    }

    public void rotateFile() {
        lock.lock();
        try {
            if (currentFile != null) {
                currentFile.close();
                files.add(currentFile);
                currentFile = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void maintain() {
        try {
            writeToFile();
        } catch (IOException e) {
            // Ignore
        }
        if (currentFile != null && currentFile.getSize() > MAX_FILE_SIZE) {
            rotateFile();
        }
        while (files.size() > MAX_FILES) {
            TempGzipFile file = files.poll();
            if (file != null) {
                file.delete();
            }
        }
        if (suspendUntil != null && suspendUntil < System.currentTimeMillis()) {
            suspendUntil = null;
        }
    }

    public void clear() {
        pendingWrites.clear();
        rotateFile();
        for (TempGzipFile file : files) {
            file.delete();
        }
        files.clear();
    }

    public void close() {
        enabled = false;
        stopMaintenance();
        clear();
    }

    private void startMaintenance() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "apitally-request-logger");
                thread.setDaemon(true);
                return thread;
            });
        }
        maintainTask = scheduler.scheduleAtFixedRate(
                this::maintain,
                0,
                MAINTAIN_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void stopMaintenance() {
        if (maintainTask != null) {
            maintainTask.cancel(false);
            maintainTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean shouldExcludePath(String path) {
        if (path == null) {
            return false;
        }
        return compiledPathExcludePatterns.stream()
                .anyMatch(p -> p.matcher(path).find());
    }

    private boolean shouldExcludeUserAgent(String userAgent) {
        return userAgent != null && compiledUserAgentExcludePatterns.stream()
                .anyMatch(p -> p.matcher(userAgent).find());
    }

    private boolean shouldMaskQueryParam(String name) {
        return compiledQueryParamMaskPatterns.stream()
                .anyMatch(p -> p.matcher(name).find());
    }

    private boolean shouldMaskHeader(String name) {
        return compiledHeaderMaskPatterns.stream()
                .anyMatch(p -> p.matcher(name).find());
    }

    private String maskQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        StringBuilder result = new StringBuilder();
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String[] pair = pairs[i].split("=", 2);
            String name = pair[0];
            String value = pair.length > 1 ? pair[1] : "";
            if (i > 0) {
                result.append('&');
            }
            result.append(name).append('=');
            result.append(shouldMaskQueryParam(name) ? MASKED : value);
        }
        return result.toString();
    }

    private List<Header> maskHeaders(Header[] headers) {
        return Arrays.stream(headers)
                .map(header -> new Header(
                        header.getName(),
                        shouldMaskHeader(header.getName()) ? MASKED : header.getValue()))
                .collect(Collectors.toList());
    }

    private boolean hasSupportedContentType(Header[] headers) {
        String contentType = findHeader(headers, "content-type");
        return contentType != null && ALLOWED_CONTENT_TYPES.stream()
                .anyMatch(contentType::startsWith);
    }

    private String findHeader(Header[] headers, String name) {
        return Arrays.stream(headers)
                .filter(h -> h.getName().toLowerCase().equals(name))
                .map(Header::getValue)
                .findFirst()
                .orElse(null);
    }

    private ObjectNode skipEmptyValues(ObjectNode node) {
        ObjectNode result = objectMapper.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().isNull()) {
                return;
            }
            if (entry.getValue().isArray() && entry.getValue().size() == 0) {
                return;
            }
            if (entry.getValue().isTextual() && entry.getValue().asText().isEmpty()) {
                return;
            }
            result.set(entry.getKey(), entry.getValue());
        });
        return result;
    }
}
