package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

class ReliableMessageProtocolSupportTest {

    private final ReliableMessageProtocolSupport support = new ReliableMessageProtocolSupport();

    @Test
    void parsesMessageReferenceAndClampsRetryAttempt() {
        String eventId = UUID.randomUUID().toString();
        Message message = MessageBuilder.withBody(new byte[] {1})
                .setMessageId(eventId + ":2")
                .setHeader("cc4c-retry-attempt", 9)
                .build();

        assertEquals(
                new ReliableMessageProtocolSupport.MessageReference(eventId, 2),
                support.messageReference(message).orElseThrow());
        assertEquals(3, support.retryAttempt(message, 3));
        assertTrue(
                support.messageReference(MessageBuilder.withBody(new byte[] {1}).build())
                        .isEmpty());
    }

    @Test
    void preservesMessageProtocolHeadersForRetryAndDeadCopies() {
        String eventId = UUID.randomUUID().toString();
        MessageEnvelope envelope = new MessageEnvelope(
                eventId,
                AsyncEventTypes.BLOG_SUBMITTED,
                1,
                0,
                Instant.parse("2026-08-28T00:00:00Z"),
                null,
                "test-v1",
                new byte[12],
                new byte[] {1, 2, 3});
        Message original = MessageBuilder.withBody(new byte[] {9}).build();

        Message retry = support.copyForRetry(original, envelope, 1);
        Message dead = support.copyForDead(original, envelope, "INVALID_ENVELOPE");

        assertEquals(eventId + ":0", retry.getMessageProperties().getMessageId());
        assertEquals(1, retry.getMessageProperties().getHeaders().get("cc4c-retry-attempt"));
        assertEquals(eventId + ":0", dead.getMessageProperties().getMessageId());
        assertEquals(
                "INVALID_ENVELOPE", dead.getMessageProperties().getHeaders().get("cc4c-error-code"));
        assertEquals(
                MessageProperties.CONTENT_TYPE_JSON,
                retry.getMessageProperties().getContentType());
    }
}
