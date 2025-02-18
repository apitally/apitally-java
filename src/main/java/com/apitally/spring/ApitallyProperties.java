package com.apitally.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@ConfigurationProperties(prefix = "apitally")
@Validated
public class ApitallyProperties {
    @NotNull(message = "Client ID must be set")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "Client ID must be a valid UUID")
    private String clientId;
    @Pattern(regexp = "^[\\w-]{1,32}$", message = "Env must be 1-32 characters long and contain only word characters and hyphens")
    private String env = "default";
    private RequestLoggingConfig requestLogging = new RequestLoggingConfig();

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public RequestLoggingConfig getRequestLogging() {
        return requestLogging;
    }

    public void setRequestLogging(RequestLoggingConfig requestLogging) {
        this.requestLogging = requestLogging;
    }

    public static class RequestLoggingConfig extends com.apitally.common.RequestLoggingConfig {
    }
}
