package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessagePayloadCipherTest {
    private static final String OLD_KEY = key("0123456789abcdef0123456789abcdef");
    private static final String NEW_KEY = key("abcdef0123456789abcdef0123456789");

    @Test
    void encryptsPayloadAndBindsEnvelopeMetadataAsAad() {
        MessagePayloadCipher cipher = cipher("old", "old=" + OLD_KEY);
        Instant occurredAt = Instant.parse("2026-08-28T00:00:00Z");
        Instant expiresAt = occurredAt.plusSeconds(600);
        byte[] expected = "{\"email\":\"private@example.com\",\"code\":\"123456\"}".getBytes(StandardCharsets.UTF_8);

        EncryptedMessagePayload encrypted = cipher.encryptBytes(
                "event-1", AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED, 1, 0, occurredAt, expiresAt, expected);
        MessageEnvelope envelope =
                envelope(encrypted, "event-1", AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED, 0, occurredAt, expiresAt);

        assertArrayEquals(expected, cipher.decrypt(envelope));
        assertFalse(new String(encrypted.ciphertext(), StandardCharsets.UTF_8).contains("private@example.com"));

        MessageEnvelope tampered =
                envelope(encrypted, "event-1", AsyncEventTypes.BLOG_SUBMITTED, 0, occurredAt, expiresAt);
        MessagePayloadException exception = assertThrows(MessagePayloadException.class, () -> cipher.decrypt(tampered));
        assertEquals("DECRYPTION_FAILED", exception.errorCode());
    }

    @Test
    void decryptsOldKeyAfterRotationAndRejectsUnknownKey() {
        Instant occurredAt = Instant.parse("2026-08-28T00:00:00Z");
        MessagePayloadCipher oldCipher = cipher("old", "old=" + OLD_KEY);
        EncryptedMessagePayload encrypted = oldCipher.encryptBytes(
                "event-2",
                AsyncEventTypes.BLOG_REVIEWED,
                1,
                0,
                occurredAt,
                null,
                "payload".getBytes(StandardCharsets.UTF_8));

        MessagePayloadCipher rotated = cipher("new", "new=" + NEW_KEY + ";old=" + OLD_KEY);
        assertArrayEquals(
                "payload".getBytes(StandardCharsets.UTF_8),
                rotated.decrypt(envelope(encrypted, "event-2", AsyncEventTypes.BLOG_REVIEWED, 0, occurredAt, null)));

        MessageEnvelope unknown = new MessageEnvelope(
                "event-2",
                AsyncEventTypes.BLOG_REVIEWED,
                1,
                0,
                occurredAt,
                null,
                "missing",
                encrypted.nonce(),
                encrypted.ciphertext());
        MessagePayloadException exception = assertThrows(MessagePayloadException.class, () -> rotated.decrypt(unknown));
        assertEquals("UNKNOWN_KEY_ID", exception.errorCode());
    }

    @Test
    void rejectsPlaintextLargerThanSixtyFourKibibytes() {
        MessagePayloadCipher cipher = cipher("old", "old=" + OLD_KEY);
        MessagePayloadException exception = assertThrows(
                MessagePayloadException.class,
                () -> cipher.encrypt(
                        "event-3",
                        AsyncEventTypes.BLOG_SUBMITTED,
                        1,
                        0,
                        Instant.now(),
                        null,
                        Map.of("body", "x".repeat(MessagePayloadCipher.MAX_PLAINTEXT_BYTES))));
        assertEquals("PAYLOAD_TOO_LARGE", exception.errorCode());
    }

    private MessagePayloadCipher cipher(String activeKeyId, String keys) {
        MessagingProperties properties = new MessagingProperties(
                "cc4c.test.messaging",
                activeKeyId,
                keys,
                "reviewer@example.com",
                Duration.ofSeconds(5),
                List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                Duration.ofMillis(500),
                false,
                false);
        return new MessagePayloadCipher(new ObjectMapper(), properties);
    }

    private MessageEnvelope envelope(
            EncryptedMessagePayload encrypted,
            String eventId,
            String eventType,
            int generation,
            Instant occurredAt,
            Instant expiresAt) {
        return new MessageEnvelope(
                eventId,
                eventType,
                1,
                generation,
                occurredAt,
                expiresAt,
                encrypted.keyId(),
                encrypted.nonce(),
                encrypted.ciphertext());
    }

    private static String key(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }
}
