package com.apitally.common;

public class ApitallyClient {
    private final String clientId;
    private final String env;

    public final RequestCounter requestCounter;
    public final ServerErrorCounter serverErrorCounter;
    public final ConsumerRegistry consumerRegistry;

    public ApitallyClient(String clientId, String env) {
        this.clientId = clientId;
        this.env = env;

        this.requestCounter = new RequestCounter();
        this.serverErrorCounter = new ServerErrorCounter();
        this.consumerRegistry = new ConsumerRegistry();
    }

}
