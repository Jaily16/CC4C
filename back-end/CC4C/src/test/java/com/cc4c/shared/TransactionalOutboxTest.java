package com.cc4c.shared;

import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionalOutboxTest {

    @Test
    void databaseFailureBecomesControlledServiceUnavailableWithoutExposingPayload() {
        OutboxRepository repository = mock(OutboxRepository.class);
        MessagePayloadCipher cipher = mock(MessagePayloadCipher.class);
        when(cipher.encrypt(any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new EncryptedMessagePayload("test-v1", new byte[12], new byte[]{1}));
        doThrow(new TransientDataAccessResourceException("database offline"))
                .when(repository).insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        TransactionalOutbox outbox = new TransactionalOutbox(repository, cipher);

        BusinessException failure = assertThrows(BusinessException.class, () -> outbox.append(
                AsyncEventTypes.BLOG_SUBMITTED,
                "blog",
                "42",
                Map.of("recipientEmail", "secret@example.com"),
                Instant.now(),
                null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
        assertEquals("异步消息暂时无法可靠受理", failure.getMessage());
    }
}
