package com.cc4c.shared;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingPropertiesTest {

    @Test
    void moderationRecipientsAreValidatedNormalizedAndDeduplicatedAtConstruction() {
        MessagingProperties properties = properties(
                " Reviewer@Example.com,reviewer@example.com, second@example.com ");

        assertEquals(
                List.of("reviewer@example.com", "second@example.com"),
                properties.moderationRecipientList());
        assertThrows(IllegalStateException.class, () -> properties("not-an-email"));
        assertThrows(IllegalStateException.class, () -> properties(" , "));
    }

    private MessagingProperties properties(String recipients) {
        return new MessagingProperties(
                "cc4c.test.messaging",
                "test-v1",
                "test-v1=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                recipients,
                Duration.ofSeconds(5),
                List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                Duration.ofMillis(500),
                false,
                false);
    }
}
