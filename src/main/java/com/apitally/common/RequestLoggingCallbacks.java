package com.apitally.common;

import com.apitally.common.dto.Request;
import com.apitally.common.dto.Response;

public interface RequestLoggingCallbacks {
    /**
     * Mask sensitive data in the request body.
     */
    default byte[] maskRequestBody(Request request) {
        return request.getBody();
    }

    /**
     * Mask sensitive data in the response body.
     */
    default byte[] maskResponseBody(Request request, Response response) {
        return response.getBody();
    }

    /**
     * Determine whether a request should be excluded from logging.
     * Return true to exclude the request.
     */
    default boolean shouldExclude(Request request, Response response) {
        return false;
    }
}
