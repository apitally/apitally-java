package com.apitally.common.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StartupData {
    private final UUID instanceUuid;
    private final UUID messageUuid;
    private final List<PathItem> paths;
    private final Map<String, String> versions;
    private final String client;

    @JsonCreator
    public StartupData(
            @JsonProperty("instance_uuid") UUID instanceUuid,
            @JsonProperty("paths") List<PathItem> paths,
            @JsonProperty("versions") Map<String, String> versions,
            @JsonProperty("client") String client) {
        this.instanceUuid = instanceUuid;
        this.messageUuid = UUID.randomUUID();
        this.paths = paths;
        this.versions = versions;
        this.client = client;
    }

    @JsonProperty("instance_uuid")
    public UUID getInstanceUuid() {
        return instanceUuid;
    }

    @JsonProperty("message_uuid")
    public UUID getMessageUuid() {
        return messageUuid;
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
