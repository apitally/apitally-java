package com.apitally.spring;

import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ApitallyFilter extends OncePerRequestFilter {

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

            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            try {
                System.out.printf(
                        "Request method: %s, Request URL: %s, Route pattern: %s, Response time: %d ms, Status code: %d%n",
                        request.getMethod(), request.getRequestURL(), pattern, responseTimeInMillis,
                        response.getStatus());
                System.out.printf("Response body: %s%n", new String(responseBody));
            } catch (Exception exception) {
                // TODO: Implement
            }
        }
    }
}
