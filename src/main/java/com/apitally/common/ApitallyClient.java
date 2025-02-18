package com.apitally.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

import com.apitally.common.dto.Path;
import com.apitally.common.dto.StartupData;
import com.apitally.common.dto.SyncData;

public class ApitallyClient {
    private static class RetryableHubRequestException extends Exception {
        public RetryableHubRequestException(String message) {
            super(message);
        }
    }

    private enum HubRequestStatus {
        OK,
        VALIDATION_ERROR,
        INVALID_CLIENT_ID,
        RETRYABLE_ERROR
    }

    private static final int SYNC_INTERVAL_SECONDS = 60;
    private static final int INITIAL_SYNC_INTERVAL_SECONDS = 10;
    private static final int INITIAL_PERIOD_SECONDS = 3600;
    private static final int MAX_QUEUE_TIME_SECONDS = 3600;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;
    private static final String HUB_BASE_URL = Optional.ofNullable(System.getenv("APITALLY_HUB_BASE_URL"))
            .filter(s -> !s.trim().isEmpty())
            .orElse("https://hub.apitally.io");

    private static final Logger logger = LoggerFactory.getLogger(ApitallyClient.class);
    private static final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(Duration.ofSeconds(1), 2, Duration.ofSeconds(4), true)
            .retryOn(RetryableHubRequestException.class)
            .build();

    private static ApitallyClient instance;
    private final String clientId;
    private final String env;
    private final UUID instanceUuid;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private ScheduledFuture<?> syncTask;
    private StartupData startupData;
    private boolean startupDataSent = false;

    public final RequestCounter requestCounter;
    public final RequestLogger requestLogger;
    public final ServerErrorCounter serverErrorCounter;
    public final ConsumerRegistry consumerRegistry;

    private final Queue<SyncData> syncDataQueue = new ConcurrentLinkedQueue<SyncData>();
    private final Random random = new Random();

    private ApitallyClient(String clientId, String env, RequestLoggingConfig requestLoggingConfig) {
        this.clientId = clientId;
        this.env = env;
        this.instanceUuid = java.util.UUID.randomUUID();

        this.requestCounter = new RequestCounter();
        this.requestLogger = new RequestLogger(requestLoggingConfig);
        this.serverErrorCounter = new ServerErrorCounter();
        this.consumerRegistry = new ConsumerRegistry();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "apitally-sync");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        startSync();
    }

    public static synchronized ApitallyClient getInstance(String clientId, String env,
            RequestLoggingConfig requestLoggingConfig) {
        if (instance == null) {
            instance = new ApitallyClient(clientId, env, requestLoggingConfig);
        } else if (!instance.clientId.equals(clientId) || !instance.env.equals(env)) {
            throw new IllegalStateException(
                    String.format(
                            "ApitallyClient instance already exists with different parameters: Existing (clientId: %s, env: %s), Requested (clientId: %s, env: %s)",
                            instance.clientId, instance.env, clientId, env));
        }
        return instance;
    }

    private URI getHubUrl(String endpoint) {
        String baseUrl = HUB_BASE_URL.replaceAll("/+$", "");
        return URI.create(baseUrl + "/v2/" + clientId + "/" + env + "/" + endpoint);
    }

    public void setStartupData(List<Path> paths, Map<String, String> versions, String client) {
        startupData = new StartupData(instanceUuid, paths, versions, client);
        sendStartupData();
    }

    private void sendStartupData() {
        if (startupData == null) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(getHubUrl("startup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(startupData.toJSON()))
                .build();
        sendHubRequest(request).thenAccept(status -> {
            if (status == HubRequestStatus.OK) {
                startupDataSent = true;
                startupData = null;
            } else if (status == HubRequestStatus.VALIDATION_ERROR) {
                startupDataSent = false;
                startupData = null;
            }
        });
    }

    private void sendSyncData() {
        SyncData data = new SyncData(
                instanceUuid,
                requestCounter.getAndResetRequests(),
                serverErrorCounter.getAndResetServerErrors(),
                consumerRegistry.getAndResetUpdatedConsumers());
        syncDataQueue.offer(data);

        int i = 0;
        while (!syncDataQueue.isEmpty()) {
            SyncData payload = syncDataQueue.poll();
            if (payload != null) {
                try {
                    if (payload.getAgeInSeconds() <= MAX_QUEUE_TIME_SECONDS) {
                        if (i > 0) {
                            // Add random delay between retries
                            Thread.sleep(100 + random.nextInt(400));
                        }
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(getHubUrl("sync"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSON()))
                                .build();
                        sendHubRequest(request);
                        i++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private CompletableFuture<HubRequestStatus> sendHubRequest(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return retryTemplate.execute(context -> {
                    try {
                        logger.debug("Sending request to Apitally hub: {}", request.uri());
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return HubRequestStatus.OK;
                        } else if (response.statusCode() == 404) {
                            stopSync();
                            logger.error("Invalid Apitally client ID: {}", clientId);
                            return HubRequestStatus.INVALID_CLIENT_ID;
                        } else if (response.statusCode() == 422) {
                            logger.error("Received validation error from Apitally hub: {}", response.body());
                            return HubRequestStatus.VALIDATION_ERROR;
                        } else {
                            throw new RetryableHubRequestException(
                                    "Hub request failed with status code " + response.statusCode());
                        }
                    } catch (Exception e) {
                        throw new RetryableHubRequestException(
                                "Hub request failed with exception: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                logger.error("Error sending request to Apitally hub", e);
                return HubRequestStatus.RETRYABLE_ERROR;
            }
        });
    }

    private void startSync() {
        // Start with shorter initial sync interval
        syncTask = scheduler.scheduleAtFixedRate(
                this::sync,
                0,
                INITIAL_SYNC_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        // Schedule a one-time task to switch to regular sync interval
        scheduler.schedule(() -> {
            syncTask.cancel(false);
            syncTask = scheduler.scheduleAtFixedRate(
                    this::sync,
                    SYNC_INTERVAL_SECONDS,
                    SYNC_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
        }, INITIAL_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    private void stopSync() {
        syncTask.cancel(false);
        syncTask = null;
        scheduler.shutdown();
    }

    private void sync() {
        sendSyncData();
        if (!startupDataSent) {
            sendStartupData();
        }
    }

    public void shutdown() {
        try {
            stopSync();
            sync();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
