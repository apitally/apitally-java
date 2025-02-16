package com.apitally.common;

import org.springframework.stereotype.Component;

@Component
public class ApitallyClient {
    private final String clientId;
    private final String environment;

    public ApitallyClient(String clientId, String environment) {
        this.clientId = clientId;
        this.environment = environment;
    }
}
