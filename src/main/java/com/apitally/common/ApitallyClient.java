package com.apitally.common;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.apitally.common.dto.StartupData;

public class ApitallyClient {
    private static final long SYNC_INTERVAL_SECONDS = 60;
    private static final long INITIAL_SYNC_INTERVAL_SECONDS = 10;
    // private static final long INITIAL_PERIOD_SECONDS = 3600;
    private static final long INITIAL_PERIOD_SECONDS = 30;

    private static ApitallyClient instance;
    private final String clientId;
    private final String env;
    private final UUID instanceUuid;
    private final long startTime;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> syncTask;
    private StartupData startupData;

    public final RequestCounter requestCounter;
    public final ServerErrorCounter serverErrorCounter;
    public final ConsumerRegistry consumerRegistry;

    private ApitallyClient(String clientId, String env) {
        this.clientId = clientId;
        this.env = env;
        this.instanceUuid = java.util.UUID.randomUUID();

        this.requestCounter = new RequestCounter();
        this.serverErrorCounter = new ServerErrorCounter();
        this.consumerRegistry = new ConsumerRegistry();

        this.startTime = System.currentTimeMillis();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "apitally-sync");
            thread.setDaemon(true);
            return thread;
        });

        startSync();
    }

    public static synchronized ApitallyClient getInstance(String clientId, String env) {
        if (instance == null) {
            instance = new ApitallyClient(clientId, env);
        } else if (!instance.clientId.equals(clientId) || !instance.env.equals(env)) {
            throw new IllegalStateException(
                    String.format(
                            "ApitallyClient instance already exists with different parameters: Existing (clientId: %s, env: %s), Requested (clientId: %s, env: %s)",
                            instance.clientId, instance.env, clientId, env));
        }
        return instance;
    }

    public void setStartupData(StartupData startupData) {
        this.startupData = startupData;
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

    private void sync() {
        try {
            System.out.println(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date())
                    + " Syncing with Apitally Hub");
            // Implement sync logic here
            // sendSyncData();
            // sendLogData();
            // if (!startupDataSent) {
            // sendStartupData();
            // }
        } catch (Exception e) {
            // logger.error("Error while syncing with Apitally Hub", e);
        }
    }

    public void shutdown() {
        try {
            syncTask.cancel(false);
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            // Perform final sync
            sync();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
