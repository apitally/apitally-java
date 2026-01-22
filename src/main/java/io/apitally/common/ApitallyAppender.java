package io.apitally.common;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import io.apitally.common.dto.LogRecord;

public class ApitallyAppender extends AppenderBase<ILoggingEvent> {
    private static final String NAME = "ApitallyAppender";
    private static final int MAX_BUFFER_SIZE = 1000;
    private static final int MAX_MESSAGE_LENGTH = 2048;

    private static final ThreadLocal<List<LogRecord>> logBuffer = new ThreadLocal<>();

    public static synchronized void register() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
            return;
        }
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        if (rootLogger.getAppender(NAME) != null) {
            return;
        }

        ApitallyAppender appender = new ApitallyAppender();
        appender.setContext(loggerContext);
        appender.setName(NAME);
        appender.start();
        rootLogger.addAppender(appender);
    }

    public static void startCapture() {
        logBuffer.set(new ArrayList<>());
    }

    public static List<LogRecord> endCapture() {
        List<LogRecord> logs = logBuffer.get();
        logBuffer.remove();
        return logs;
    }

    @Override
    protected void append(ILoggingEvent event) {
        List<LogRecord> buffer = logBuffer.get();
        if (buffer == null || buffer.size() >= MAX_BUFFER_SIZE) {
            return;
        }

        double timestamp = event.getTimeStamp() / 1000.0;
        String loggerName = event.getLoggerName();
        String level = event.getLevel().toString();
        String message = truncateMessage(event.getFormattedMessage());

        buffer.add(new LogRecord(timestamp, loggerName, level, message));
    }

    private static String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        String suffix = "... (truncated)";
        return message.substring(0, MAX_MESSAGE_LENGTH - suffix.length()) + suffix;
    }
}
