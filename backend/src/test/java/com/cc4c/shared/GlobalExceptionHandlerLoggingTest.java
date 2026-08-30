package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GlobalExceptionHandlerLoggingTest {
    @Test
    void unexpectedFailureLogDoesNotExposeExceptionMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new GlobalExceptionHandler()
                    .handleUnexpectedException(
                            new IllegalStateException("user@example.com password=do-not-log jdbc:mysql://secret"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String captured = appender.list.stream()
                .map(event -> event.getFormattedMessage() + " "
                        + event.getKeyValuePairs().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(" ")))
                .collect(Collectors.joining("\n"));
        assertFalse(captured.contains("user@example.com"));
        assertFalse(captured.contains("do-not-log"));
        assertFalse(captured.contains("jdbc:mysql"));
        assertTrue(appender.list.stream().allMatch(event -> event.getLevel() == Level.ERROR));
        assertTrue(captured.contains("exception_type"));
        assertTrue(captured.contains("exception_fingerprint"));
    }
}
