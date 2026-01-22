package io.apitally.common.dto;

public class LogRecord {
    private final double timestamp;
    private final String logger;
    private final String level;
    private final String message;

    public LogRecord(double timestamp, String logger, String level, String message) {
        this.timestamp = timestamp;
        this.logger = logger;
        this.level = level;
        this.message = message;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public String getLogger() {
        return logger;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
