package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;

class TransactionalOutboxTest {

    @Test
    void storesCurrentRequestCorrelationId() {
        OutboxRepository repository = mock(OutboxRepository.class);
        TransactionalOutbox outbox = outbox(repository);

        try (CorrelationIds.Scope ignored = CorrelationIds.open("request-correlation-1234")) {
            outbox.append(AsyncEventTypes.BLOG_SUBMITTED, "blog", "42", Map.of("title", "safe"), Instant.now(), null);
        }

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(repository)
                .insert(any(), correlationId.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertEquals("request-correlation-1234", correlationId.getValue());
    }

    @Test
    void usesEventIdWhenThereIsNoHttpCorrelationContext() {
        OutboxRepository repository = mock(OutboxRepository.class);
        TransactionalOutbox outbox = outbox(repository);

        String eventId = outbox.append(
                AsyncEventTypes.BLOG_SUBMITTED, "blog", "42", Map.of("title", "safe"), Instant.now(), null);

        ArgumentCaptor<String> persistedEventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(repository)
                .insert(
                        persistedEventId.capture(),
                        correlationId.capture(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
        assertEquals(eventId, persistedEventId.getValue());
        assertEquals(eventId, correlationId.getValue());
    }

    @Test
    void databaseFailureBecomesControlledServiceUnavailableWithoutExposingPayload() {
        OutboxRepository repository = mock(OutboxRepository.class);
        MessagePayloadCipher cipher = cipher();
        doThrow(new TransientDataAccessResourceException("database offline"))
                .when(repository)
                .insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        TransactionalOutbox outbox = new TransactionalOutbox(repository, cipher);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> outbox.append(
                        AsyncEventTypes.BLOG_SUBMITTED,
                        "blog",
                        "42",
                        Map.of("recipientEmail", "secret@example.com"),
                        Instant.now(),
                        null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
        assertEquals("异步消息暂时无法可靠受理", failure.getMessage());
    }

    private TransactionalOutbox outbox(OutboxRepository repository) {
        return new TransactionalOutbox(repository, cipher());
    }

    private MessagePayloadCipher cipher() {
        MessagePayloadCipher cipher = mock(MessagePayloadCipher.class);
        when(cipher.encrypt(any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new EncryptedMessagePayload("test-v1", new byte[12], new byte[] {1}));
        return cipher;
    }
}
