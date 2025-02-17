package com.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class StartupData {
    private final List<PathItem> paths;
    private final Map<String, String> versions;
    private final String client;

    @JsonCreator
    public StartupData(@JsonProperty("paths") List<PathItem> paths,
            @JsonProperty("versions") Map<String, String> versions,
            @JsonProperty("client") String client) {
        this.paths = paths;
        this.versions = versions;
        this.client = client;
    }

    public List<PathItem> getPaths() {
        return paths;
    }

    public Map<String, String> getVersions() {
        return versions;
    }

    public String getClient() {
        return client;
    }
}
