package com.apitally.spring;

import java.io.IOException;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

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

                // Add validation error to counter
                if (response.getStatus() >= 400 && response.getStatus() < 500) {
                    Object capturedException = request.getAttribute("apitallyCapturedException");
                    if (capturedException instanceof ConstraintViolationException e) {
                        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
                            client.validationErrorCounter.addValidationError(consumerIdentifier, request.getMethod(),
                                    path, violation.getPropertyPath().toString(), violation.getMessage(),
                                    violation.getConstraintDescriptor().getAnnotation().annotationType()
                                            .getSimpleName());
                        }
                    } else if (capturedException instanceof MethodArgumentNotValidException e) {
                        for (FieldError error : e.getBindingResult().getFieldErrors()) {
                            client.validationErrorCounter.addValidationError(consumerIdentifier, request.getMethod(),
                                    path, error.getObjectName() + "." + error.getField(),
                                    error.getDefaultMessage(),
                                    error.getCode());
                        }
                    }
                }

                // Add server error to counter
                if (response.getStatus() == 500) {
                    Object capturedException = request.getAttribute("apitallyCapturedException");
                    if (exception == null && capturedException != null && capturedException instanceof Exception) {
                        exception = (Exception) capturedException;
                    }
                    if (exception != null) {
                        client.serverErrorCounter.addServerError(consumerIdentifier, request.getMethod(), path,
                                exception);
                    }
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
