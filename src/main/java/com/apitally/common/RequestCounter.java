package com.apitally.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.apitally.common.dto.Request;
import com.apitally.common.dto.RequestsItem;

public class RequestCounter {
    private final Map<String, Integer> requestCounts;
    private final Map<String, Long> requestSizeSums;
    private final Map<String, Long> responseSizeSums;
    private final Map<String, Map<Integer, Integer>> responseTimes;
    private final Map<String, Map<Integer, Integer>> requestSizes;
    private final Map<String, Map<Integer, Integer>> responseSizes;

    public RequestCounter() {
        this.requestCounts = new HashMap<>();
        this.requestSizeSums = new HashMap<>();
        this.responseSizeSums = new HashMap<>();
        this.responseTimes = new HashMap<>();
        this.requestSizes = new HashMap<>();
        this.responseSizes = new HashMap<>();
    }

    private String getKey(Request requestInfo) {
        return String.join("|",
                requestInfo.getConsumer() != null ? requestInfo.getConsumer() : "",
                requestInfo.getMethod().toUpperCase(),
                requestInfo.getPath(),
                String.valueOf(requestInfo.getStatusCode()));
    }

    public void addRequest(Request requestInfo) {
        String key = getKey(requestInfo);

        // Increment request count
        requestCounts.merge(key, 1, Integer::sum);

        // Add response time (rounded to nearest 10ms)
        responseTimes.computeIfAbsent(key, k -> new HashMap<>());
        Map<Integer, Integer> responseTimeMap = responseTimes.get(key);
        int responseTimeMsBin = (int) (Math.floor(requestInfo.getResponseTime() / 10.0) * 10);
        responseTimeMap.merge(responseTimeMsBin, 1, Integer::sum);

        // Add request size (rounded down to nearest KB)
        if (requestInfo.getRequestSize() != null) {
            long requestSize = Long.parseLong(String.valueOf(requestInfo.getRequestSize()));
            requestSizeSums.merge(key, requestSize, Long::sum);
            requestSizes.computeIfAbsent(key, k -> new HashMap<>());
            Map<Integer, Integer> requestSizeMap = requestSizes.get(key);
            int requestSizeKbBin = (int) Math.floor(requestSize / 1000.0);
            requestSizeMap.merge(requestSizeKbBin, 1, Integer::sum);
        }

        // Add response size (rounded down to nearest KB)
        if (requestInfo.getResponseSize() != null) {
            long responseSize = Long.parseLong(String.valueOf(requestInfo.getResponseSize()));
            responseSizeSums.merge(key, responseSize, Long::sum);
            responseSizes.computeIfAbsent(key, k -> new HashMap<>());
            Map<Integer, Integer> responseSizeMap = responseSizes.get(key);
            int responseSizeKbBin = (int) Math.floor(responseSize / 1000.0);
            responseSizeMap.merge(responseSizeKbBin, 1, Integer::sum);
        }
    }

    public List<RequestsItem> getAndResetRequests() {
        List<RequestsItem> data = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : requestCounts.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split("\\|");
            String consumer = parts[0].isEmpty() ? null : parts[0];
            String method = parts[1];
            String path = parts[2];
            int statusCode = Integer.parseInt(parts[3]);

            Map<Integer, Integer> responseTimeMap = responseTimes.getOrDefault(key, new HashMap<>());
            Map<Integer, Integer> requestSizeMap = requestSizes.getOrDefault(key, new HashMap<>());
            Map<Integer, Integer> responseSizeMap = responseSizes.getOrDefault(key, new HashMap<>());

            RequestsItem item = new RequestsItem(
                    consumer,
                    method,
                    path,
                    statusCode,
                    entry.getValue(),
                    requestSizeSums.getOrDefault(key, 0L),
                    responseSizeSums.getOrDefault(key, 0L),
                    responseTimeMap,
                    requestSizeMap,
                    responseSizeMap);
            data.add(item);
        }

        // Reset all counters
        requestCounts.clear();
        requestSizeSums.clear();
        responseSizeSums.clear();
        responseTimes.clear();
        requestSizes.clear();
        responseSizes.clear();

        return data;
    }
}
