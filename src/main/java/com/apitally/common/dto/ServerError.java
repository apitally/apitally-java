package com.apitally.common.dto;

import java.util.ArrayList;
import java.util.List;

public class ServerError {
    private static final int MAX_MSG_LENGTH = 2048;
    private static final int MAX_STACKTRACE_LENGTH = 65536;

    private final String consumer;
    private final String method;
    private final String path;
    private final String type;
    private final String message;
    private final String stacktrace;

    public ServerError(String consumer, String method, String path, String type, String message,
            String stacktrace) {
        this.consumer = consumer;
        this.method = method;
        this.path = path;
        this.type = type;
        this.message = truncateMessage(message);
        this.stacktrace = truncateStacktrace(stacktrace);
    }

    public String getConsumer() {
        return consumer;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    private String truncateMessage(String msg) {
        msg = msg.trim();
        if (msg.length() <= MAX_MSG_LENGTH) {
            return msg;
        }
        String suffix = "... (truncated)";
        int cutoff = MAX_MSG_LENGTH - suffix.length();
        return msg.substring(0, cutoff) + suffix;
    }

    private String truncateStacktrace(String stacktrace) {
        String suffix = "... (truncated) ...";
        int cutoff = MAX_STACKTRACE_LENGTH - suffix.length();
        String[] lines = stacktrace.trim().split("\n");
        List<String> truncatedLines = new ArrayList<>();
        int length = 0;
        for (String line : lines) {
            if (length + line.length() + 1 > cutoff) {
                truncatedLines.add(suffix);
                break;
            }
            truncatedLines.add(line);
            length += line.length() + 1;
        }
        return String.join("\n", truncatedLines);
    }
}
