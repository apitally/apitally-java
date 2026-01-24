package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.apitally.common.dto.LogRecord;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class LogAppenderTest {

    private LogAppender appender;
    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender = new LogAppender();
        appender.setContext(loggerContext);
        appender.setName("TestApitallyAppender");
        appender.start();
    }

    @AfterEach
    void tearDown() {
        LogAppender.endCapture();
        appender.stop();
    }

    @Test
    void testStartCaptureAndEndCapture() {
        LogAppender.startCapture();
        appender.doAppend(createLoggingEvent("Test message"));
        List<LogRecord> logs = LogAppender.endCapture();

        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals("test.logger", logs.get(0).getLogger());
        assertEquals("INFO", logs.get(0).getLevel());
        assertEquals("Test message", logs.get(0).getMessage());
    }

    @Test
    void testNoCaptureWhenNotStarted() {
        appender.doAppend(createLoggingEvent("Test message"));
        List<LogRecord> logs = LogAppender.endCapture();

        assertNull(logs);
    }

    @Test
    void testMaxBufferSizeLimit() {
        LogAppender.startCapture();
        for (int i = 0; i < 1100; i++) {
            appender.doAppend(createLoggingEvent("Message " + i));
        }
        List<LogRecord> logs = LogAppender.endCapture();

        assertNotNull(logs);
        assertEquals(1000, logs.size());
    }

    @Test
    void testMessageTruncation() {
        LogAppender.startCapture();
        appender.doAppend(createLoggingEvent("A".repeat(3000)));
        List<LogRecord> logs = LogAppender.endCapture();

        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getMessage().length() <= 2048);
        assertTrue(logs.get(0).getMessage().endsWith("... (truncated)"));
    }

    private LoggingEvent createLoggingEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("test.logger");
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        return event;
    }
}
