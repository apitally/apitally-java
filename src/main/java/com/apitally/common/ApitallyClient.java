package com.apitally.common;

public class ApitallyClient {
    private final String clientId;
    private final String environment;

    public final RequestCounter requestCounter;
    public final ServerErrorCounter serverErrorCounter;
    public final ConsumerRegistry consumerRegistry;

    public ApitallyClient(String clientId, String environment) {
        this.clientId = clientId;
        this.environment = environment;

        this.requestCounter = new RequestCounter();
        this.serverErrorCounter = new ServerErrorCounter();
        this.consumerRegistry = new ConsumerRegistry();
    }

}
