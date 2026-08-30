package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.dao.TransientDataAccessResourceException;

class OutboxPublisherTest {

    @Test
    void confirmedPublishMovesOnlyItsGenerationToPublished() {
        Fixture fixture = fixture(message(0, 0));
        when(fixture.publisher.publish(any(), any(), any(), any())).thenReturn(PublishOutcome.confirmed());

        fixture.dispatcher.dispatch();

        verify(fixture.repository).markPublished("event-1", 0);
        ArgumentCaptor<Message> published = ArgumentCaptor.forClass(Message.class);
        verify(fixture.publisher)
                .publish(eq("events"), eq(AsyncEventTypes.BLOG_SUBMITTED), published.capture(), eq("event-1:0"));
        assertEquals(
                "request-correlation-fixture",
                published.getValue().getMessageProperties().getHeader(CorrelationIds.AMQP_HEADER));
        verify(fixture.repository, never()).markPublishFailure(any(), eq(0), any(), any(), eq(false));
    }

    @Test
    void nackSchedulesFiniteBackoffWithoutMarkingPublished() {
        Fixture fixture = fixture(message(0, 0));
        when(fixture.publisher.publish(any(), any(), any(), any())).thenReturn(PublishOutcome.failed("BROKER_NACK"));

        fixture.dispatcher.dispatch();

        ArgumentCaptor<Instant> retryAt = ArgumentCaptor.forClass(Instant.class);
        verify(fixture.repository)
                .markPublishFailure(eq("event-1"), eq(0), eq("BROKER_NACK"), retryAt.capture(), eq(false));
        assertTrue(retryAt.getValue().isAfter(Instant.now().minusSeconds(1)));
        verify(fixture.repository, never()).markPublished(any(), eq(0));
    }

    @Test
    void thirdUnroutableAttemptBecomesTerminal() {
        Fixture fixture = fixture(message(2, 0));
        when(fixture.publisher.publish(any(), any(), any(), any())).thenReturn(PublishOutcome.failed("UNROUTABLE"));

        fixture.dispatcher.dispatch();

        verify(fixture.repository).markPublishFailure(eq("event-1"), eq(0), eq("UNROUTABLE"), any(), eq(true));
    }

    @Test
    void eighthBrokerFailureBecomesTerminalInsteadOfRetryingForever() {
        Fixture fixture = fixture(message(7, 0));
        when(fixture.publisher.publish(any(), any(), any(), any()))
                .thenReturn(PublishOutcome.failed("BROKER_UNAVAILABLE"));

        fixture.dispatcher.dispatch();

        verify(fixture.repository).markPublishFailure(eq("event-1"), eq(0), eq("BROKER_UNAVAILABLE"), any(), eq(true));
    }

    @Test
    void ackFollowedByDatabaseFailureIsSafelyPublishedAgainAfterLeaseRecovery() {
        Fixture fixture = fixture(message(0, 0));
        when(fixture.publisher.publish(any(), any(), any(), any())).thenReturn(PublishOutcome.confirmed());
        doThrow(new TransientDataAccessResourceException("database unavailable"))
                .doNothing()
                .when(fixture.repository)
                .markPublished("event-1", 0);

        assertThrows(TransientDataAccessResourceException.class, fixture.dispatcher::dispatch);
        fixture.dispatcher.dispatch();

        verify(fixture.publisher, times(2)).publish(any(), any(), any(), any());
        verify(fixture.repository, times(2)).markPublished("event-1", 0);
    }

    private Fixture fixture(OutboxMessage message) {
        OutboxRepository repository = mock(OutboxRepository.class);
        RabbitMessagePublisher publisher = mock(RabbitMessagePublisher.class);
        MessagingTopology topology = mock(MessagingTopology.class);
        when(topology.eventExchange()).thenReturn("events");
        when(repository.claimBatch(any(), eq(50), any())).thenReturn(List.of(message));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new Fixture(repository, publisher, new OutboxPublisher(repository, publisher, topology, objectMapper));
    }

    private OutboxMessage message(int publishAttempts, int generation) {
        Instant now = Instant.parse("2026-08-28T00:00:00Z");
        return new OutboxMessage(
                1L,
                "event-1",
                "request-correlation-fixture",
                1,
                AsyncEventTypes.BLOG_SUBMITTED,
                "blog",
                "42",
                AsyncEventTypes.BLOG_SUBMITTED,
                generation,
                OutboxStatus.PUBLISHING,
                publishAttempts,
                0,
                "test-v1",
                new byte[12],
                "ciphertext".getBytes(StandardCharsets.UTF_8),
                now,
                null,
                now,
                now,
                null,
                null);
    }

    private record Fixture(OutboxRepository repository, RabbitMessagePublisher publisher, OutboxPublisher dispatcher) {}
}
