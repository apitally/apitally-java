package com.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PathItem {
    private final String method;
    private final String path;

    @JsonCreator
    public PathItem(
            @JsonProperty("method") String method,
            @JsonProperty("path") String path) {
        this.method = method;
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }
}
