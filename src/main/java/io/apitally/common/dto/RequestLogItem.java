package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public class RequestLogItem extends BaseDto {
    private final String uuid;
    private final Request request;
    private final Response response;
    private final ExceptionDto exception;
    private final List<LogRecord> logs;

    public RequestLogItem(
            Request request, Response response, ExceptionDto exception, List<LogRecord> logs) {
        this.uuid = UUID.randomUUID().toString();
        this.request = request;
        this.response = response;
        this.exception = exception;
        this.logs = logs;
    }

    @JsonProperty("uuid")
    public String getUuid() {
        return uuid;
    }

    @JsonProperty("request")
    public Request getRequest() {
        return request;
    }

    @JsonProperty("response")
    public Response getResponse() {
        return response;
    }

    @JsonProperty("exception")
    public ExceptionDto getException() {
        return exception;
    }

    @JsonProperty("logs")
    public List<LogRecord> getLogs() {
        return logs;
    }
}
