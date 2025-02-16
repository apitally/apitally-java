package com.apitally.common.dto;

public final class ServerErrorsItem extends ServerError {
    private final int errorCount;

    public ServerErrorsItem(String consumer, String method, String path, String type, String message,
            String stacktrace, int errorCount) {
        super(consumer, method, path, type, message, stacktrace);
        this.errorCount = errorCount;
    }

    public int getErrorCount() {
        return errorCount;
    }
}
