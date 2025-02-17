package com.apitally.common.dto;

public final class ServerErrorsItem extends ServerError {
    private final int errorCount;

    public ServerErrorsItem(String consumer, String method, String path, String type, String message,
            StackTraceElement[] stackTrace, int errorCount) {
        super(consumer, method, path, type, message, stackTrace);
        this.errorCount = errorCount;
    }

    public int getErrorCount() {
        return errorCount;
    }
}
