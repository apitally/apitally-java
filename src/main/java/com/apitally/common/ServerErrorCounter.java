package com.apitally.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.apitally.common.dto.ServerError;
import com.apitally.common.dto.ServerErrorsItem;

public class ServerErrorCounter {
    private final Map<String, Integer> errorCounts;
    private final Map<String, ServerError> errorDetails;

    public ServerErrorCounter() {
        this.errorCounts = new HashMap<>();
        this.errorDetails = new HashMap<>();
    }

    public void addServerError(String consumer, String method, String path, Exception exception) {
        ServerError error = new ServerError(consumer, method, path, exception.getClass().getSimpleName(),
                exception.getMessage(), exception.getStackTrace());
        String key = getKey(error);
        errorDetails.putIfAbsent(key, error);
        errorCounts.merge(key, 1, Integer::sum);
    }

    public List<ServerErrorsItem> getAndResetServerErrors() {
        List<ServerErrorsItem> data = new ArrayList<>();
        errorCounts.forEach((key, count) -> {
            ServerError error = errorDetails.get(key);
            if (error != null) {
                data.add(new ServerErrorsItem(
                        error.getConsumer(),
                        error.getMethod(),
                        error.getPath(),
                        error.getType(),
                        error.getMessage(),
                        error.getStackTrace(),
                        count));
            }
        });
        errorCounts.clear();
        errorDetails.clear();
        return data;
    }

    private String getKey(ServerError error) {
        String hashInput = String.join("|",
                error.getConsumer() != null ? error.getConsumer() : "",
                error.getMethod(),
                error.getPath(),
                error.getType(),
                error.getMessage(),
                error.getStackTraceString());
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(hashInput.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
