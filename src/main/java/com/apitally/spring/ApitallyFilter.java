package com.apitally.spring;

import java.io.IOException;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.apitally.common.ApitallyClient;
import com.apitally.common.ConsumerRegistry;
import com.apitally.common.dto.Consumer;
import com.apitally.common.dto.Header;
import com.apitally.common.dto.Request;
import com.apitally.common.dto.Response;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApitallyFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(ApitallyFilter.class);

    private final ApitallyClient client;

    public ApitallyFilter(ApitallyClient client) {
        this.client = client;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final boolean isContentCachingEnabled = client.requestLogger.getConfig().isEnabled()
                && (client.requestLogger.getConfig().isRequestBodyIncluded() || client.requestLogger.getConfig()
                        .isResponseBodyIncluded());

        Exception exception = null;
        final long startTime = System.currentTimeMillis();
        ContentCachingRequestWrapper cachingRequest = null;
        ContentCachingResponseWrapper cachingResponse = null;

        try {
            if (isContentCachingEnabled) {
                cachingRequest = new ContentCachingRequestWrapper(request);
                cachingResponse = new ContentCachingResponseWrapper(response);
                filterChain.doFilter(cachingRequest, cachingResponse);
            } else {
                filterChain.doFilter(request, response);
            }
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            final long responseTimeInMillis = System.currentTimeMillis() - startTime;
            final String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

            // Get request and response body
            final byte[] requestBody = cachingRequest != null ? cachingRequest.getContentAsByteArray() : new byte[0];
            final byte[] responseBody = cachingResponse != null ? cachingResponse.getContentAsByteArray() : new byte[0];
            if (cachingResponse != null) {
                cachingResponse.copyBodyToResponse();
            }

            try {
                // Register consumer and get consumer identifier
                final Consumer consumer = ConsumerRegistry
                        .consumerFromStringOrObject(request.getAttribute("apitallyConsumer"));
                client.consumerRegistry.addOrUpdateConsumer(consumer);
                final String consumerIdentifier = consumer != null ? consumer.getIdentifier() : "";

                // Add request to counter
                final long requestContentLength = request.getContentLengthLong();
                final long requestSize = requestContentLength >= 0 ? requestContentLength : requestBody.length;
                final long responseContentLength = getResponseContentLength(response);
                final long responseSize = responseContentLength >= 0
                        ? responseContentLength
                        : responseBody.length;
                client.requestCounter
                        .addRequest(consumerIdentifier, request.getMethod(), path, response.getStatus(),
                                responseTimeInMillis,
                                requestSize, responseSize);

                // Add server error to counter
                if (response.getStatus() == 500 && exception != null) {
                    client.serverErrorCounter.addServerError(consumerIdentifier, request.getMethod(),
                            path, exception);
                }

                // Log request
                if (client.requestLogger.isEnabled()) {
                    final Header[] requestHeaders = Collections.list(request.getHeaderNames()).stream()
                            .flatMap(name -> Collections.list(request.getHeaders(name)).stream()
                                    .map(value -> new Header(name, value)))
                            .toArray(Header[]::new);
                    final Header[] responseHeaders = response.getHeaderNames().stream()
                            .flatMap(name -> response.getHeaders(name).stream()
                                    .map(value -> new Header(name, value)))
                            .toArray(Header[]::new);

                    client.requestLogger.logRequest(
                            new Request(startTime / 1000.0, consumerIdentifier, request.getMethod(),
                                    path,
                                    request.getRequestURL().toString(), requestHeaders, requestSize, requestBody),
                            new Response(response.getStatus(), responseTimeInMillis / 1000.0, responseHeaders,
                                    responseSize, responseBody));
                }
            } catch (Exception e) {
                logger.error("Error in Apitally filter", e);
            }
        }
    }

    private static long getResponseContentLength(HttpServletResponse response) {
        String contentLength = response.getHeader("content-length");
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
            }
        }
        return -1L;
    }
}
