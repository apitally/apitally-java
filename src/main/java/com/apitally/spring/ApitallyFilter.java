package com.apitally.spring;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.apitally.common.ApitallyClient;
import com.apitally.common.ConsumerRegistry;
import com.apitally.common.dto.Consumer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApitallyFilter extends OncePerRequestFilter {
    private final ApitallyClient apitallyClient;

    public ApitallyFilter(ApitallyClient apitallyClient) {
        this.apitallyClient = apitallyClient;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request);
        final ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);

        Exception potentialException = null;
        final long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(cachingRequest, cachingResponse);
        } catch (Exception exception) {
            potentialException = exception;
            throw exception;
        } finally {
            final long responseTimeInMillis = System.currentTimeMillis() - start;
            final byte[] requestBody = cachingRequest.getContentAsByteArray();
            final byte[] responseBody = cachingResponse.getContentAsByteArray();
            cachingResponse.copyBodyToResponse();

            String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            try {
                System.out.printf(
                        "Request method: %s, Request URL: %s, Route pattern: %s, Response time: %d ms, Status code: %d%n",
                        request.getMethod(), request.getRequestURL(), path, responseTimeInMillis,
                        response.getStatus());
                System.out.printf("Response body: %s%n", new String(responseBody));

                Consumer consumer = ConsumerRegistry
                        .consumerFromStringOrObject(request.getAttribute("apitallyConsumer"));
                apitallyClient.consumerRegistry.addOrUpdateConsumer(consumer);
                String consumerIdentifier = consumer != null ? consumer.getIdentifier() : "";

                long requestSize = request.getContentLength();
                long responseSize = responseBody.length;
                apitallyClient.requestCounter
                        .addRequest(consumerIdentifier, request.getMethod(), path, response.getStatus(),
                                responseTimeInMillis,
                                requestSize, responseSize);

                if (response.getStatus() == 500 && potentialException != null) {
                    apitallyClient.serverErrorCounter.addServerError(consumerIdentifier, request.getMethod(),
                            path, potentialException);
                }
            } catch (Exception exception) {
                // TODO: Implement
            }
        }
    }
}
