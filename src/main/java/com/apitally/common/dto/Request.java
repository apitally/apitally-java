package com.apitally.common.dto;

public final class Request {
    private final String consumer;
    private final String method;
    private final String path;
    private final int statusCode;
    private final double responseTime;
    private final Long requestSize;
    private final Long responseSize;

    public Request(String consumer, String method, String path, int statusCode, double responseTime,
            Long requestSize, Long responseSize) {
        this.consumer = consumer;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.responseTime = responseTime;
        this.requestSize = requestSize;
        this.responseSize = responseSize;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public double getResponseTime() {
        return responseTime;
    }

    public Long getRequestSize() {
        return requestSize;
    }

    public Long getResponseSize() {
        return responseSize;
    }
}
