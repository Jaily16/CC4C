package com.cc4c.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReliableMessageProcessorTest {

    @Test
    void duplicateDoneMessageIsAckedWithoutRunningHandlerAgain() throws Exception {
        Fixture fixture = fixture();
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ALREADY_DONE);

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 7L, fixture.handler);

        verify(fixture.handler, never()).handle(any(), any());
        verify(fixture.channel).basicAck(7L, false);
    }

    @Test
    void successfulHandlingCommitsInboxAndOutboxBeforeAck() throws Exception {
        Fixture fixture = fixture();
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ACQUIRED);

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 8L, fixture.handler);

        InOrder order = inOrder(fixture.handler, fixture.inbox, fixture.outbox, fixture.channel);
        order.verify(fixture.handler).handle(any(), eq("plain".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        order.verify(fixture.inbox).markDone("consumer", "event-1", 0);
        order.verify(fixture.outbox).markDelivered("event-1", 0);
        order.verify(fixture.channel).basicAck(8L, false);
    }

    @Test
    void transientFailureAcksOnlyAfterConfirmedRetryPublish() throws Exception {
        Fixture fixture = fixture();
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ACQUIRED);
        org.mockito.Mockito.doThrow(new MailDeliveryException("MAIL_TRANSIENT", false, null))
                .when(fixture.handler).handle(any(), any());
        when(fixture.topology.retryDelays()).thenReturn(List.of(
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)));
        when(fixture.topology.retryQueue(AsyncEventTypes.BLOG_SUBMITTED, 0))
                .thenReturn("retry.30s");
        when(fixture.publisher.publish(eq(""), eq("retry.30s"), any(), any()))
                .thenReturn(PublishOutcome.confirmed());

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 9L, fixture.handler);

        verify(fixture.inbox).markRetryWaiting(
                "consumer", "event-1", 0, "MAIL_TRANSIENT");
        verify(fixture.channel).basicAck(9L, false);
        verify(fixture.channel, never()).basicNack(9L, false, true);
    }

    @Test
    void unconfirmedRetryPublishRequeuesOriginalMessage() throws Exception {
        Fixture fixture = fixture();
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ACQUIRED);
        org.mockito.Mockito.doThrow(new MailDeliveryException("MAIL_TRANSIENT", false, null))
                .when(fixture.handler).handle(any(), any());
        when(fixture.topology.retryDelays()).thenReturn(List.of(
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)));
        when(fixture.topology.retryQueue(AsyncEventTypes.BLOG_SUBMITTED, 0))
                .thenReturn("retry.30s");
        when(fixture.publisher.publish(any(), any(), any(), any()))
                .thenReturn(PublishOutcome.failed("BROKER_UNAVAILABLE"));

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 10L, fixture.handler);

        verify(fixture.channel).basicNack(10L, false, true);
        verify(fixture.channel, never()).basicAck(10L, false);
    }

    @Test
    void unsupportedVersionIsPersistedAsDeadAndAckedOnlyAfterConfirmedDlqPublish() throws Exception {
        Fixture fixture = fixture(envelope(2, null));
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ACQUIRED);
        when(fixture.topology.deadExchange()).thenReturn("dead.events");
        when(fixture.publisher.publish(eq("dead.events"), any(), any(), any()))
                .thenReturn(PublishOutcome.confirmed());

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 11L, fixture.handler);

        InOrder order = inOrder(fixture.inbox, fixture.outbox, fixture.publisher, fixture.channel);
        order.verify(fixture.inbox).markDead("consumer", "event-1", 0, "UNSUPPORTED_VERSION");
        order.verify(fixture.outbox).markDead("event-1", 0, "UNSUPPORTED_VERSION");
        order.verify(fixture.publisher).publish(eq("dead.events"), any(), any(), any());
        order.verify(fixture.channel).basicAck(11L, false);
        verify(fixture.handler, never()).handle(any(), any());
    }

    @Test
    void unconfirmedDlqPublishRequeuesOriginalWithoutAcknowledgingIt() throws Exception {
        Fixture fixture = fixture(envelope(2, null));
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ACQUIRED);
        when(fixture.publisher.publish(any(), any(), any(), any()))
                .thenReturn(PublishOutcome.failed("BROKER_UNAVAILABLE"));

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 12L, fixture.handler);

        verify(fixture.channel).basicNack(12L, false, true);
        verify(fixture.channel, never()).basicAck(12L, false);
    }

    @Test
    void malformedEnvelopeUsesMessageReferenceToPersistDeadBeforeBrokerDlxReject() throws Exception {
        String eventId = "7a3dfb7b-2537-4a26-98df-7ea34650cb45";
        Message malformed = MessageBuilder.withBody("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId + ":2")
                .build();
        Fixture fixture = fixture(malformed);
        when(fixture.inbox.claim(any(), eq(eventId), eq(2), any(), any()))
                .thenReturn(InboxClaim.ACQUIRED);

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                malformed, fixture.channel, 13L, fixture.handler);

        verify(fixture.inbox).markDead("consumer", eventId, 2, "INVALID_ENVELOPE");
        verify(fixture.outbox).markDead(eventId, 2, "INVALID_ENVELOPE");
        verify(fixture.channel).basicReject(13L, false);
        verify(fixture.channel, never()).basicAck(13L, false);
    }

    @Test
    void expiredMessageIsClaimedAndCompletedWithoutCallingBusinessHandler() throws Exception {
        Fixture fixture = fixture(envelope(1, Instant.now().minusSeconds(1)));
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ACQUIRED);

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 14L, fixture.handler);

        verify(fixture.handler).expired(any(), any());
        verify(fixture.handler, never()).handle(any(), any());
        verify(fixture.inbox).markDone("consumer", "event-1", 0);
        verify(fixture.outbox).markExpired("event-1", 0);
        verify(fixture.channel).basicAck(14L, false);
    }

    @Test
    void alreadyCompletedExpiredDuplicateDoesNotOverwriteDeliveredState() throws Exception {
        Fixture fixture = fixture(envelope(1, Instant.now().minusSeconds(1)));
        when(fixture.inbox.claim(any(), any(), eq(0), any(), any()))
                .thenReturn(InboxClaim.ALREADY_DONE);

        fixture.processor.process(
                "consumer", AsyncEventTypes.BLOG_SUBMITTED,
                fixture.message, fixture.channel, 15L, fixture.handler);

        verify(fixture.handler, never()).expired(any(), any());
        verify(fixture.outbox, never()).markExpired(any(), eq(0));
        verify(fixture.channel).basicAck(15L, false);
    }

    private Fixture fixture() throws Exception {
        return fixture(envelope(1, null));
    }

    private MessageEnvelope envelope(int schemaVersion, Instant expiresAt) {
        return new MessageEnvelope(
                "event-1", AsyncEventTypes.BLOG_SUBMITTED, schemaVersion, 0,
                Instant.parse("2026-08-28T00:00:00Z"), expiresAt,
                "test-v1", new byte[12], new byte[]{1, 2, 3});
    }

    private Fixture fixture(MessageEnvelope envelope) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Message message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(envelope))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        return fixture(message);
    }

    private Fixture fixture(Message message) {
        InboxRepository inbox = mock(InboxRepository.class);
        OutboxRepository outbox = mock(OutboxRepository.class);
        MessagePayloadCipher cipher = mock(MessagePayloadCipher.class);
        RabbitMessagePublisher publisher = mock(RabbitMessagePublisher.class);
        MessagingTopology topology = mock(MessagingTopology.class);
        Channel channel = mock(Channel.class);
        ReliableMessageHandler handler = mock(ReliableMessageHandler.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(cipher.decrypt(any(MessageEnvelope.class)))
                .thenReturn("plain".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReliableMessageProcessor processor = new ReliableMessageProcessor(
                inbox, outbox, cipher, objectMapper, publisher, topology);
        return new Fixture(inbox, outbox, publisher, topology, channel, handler, processor, message);
    }

    private record Fixture(
            InboxRepository inbox,
            OutboxRepository outbox,
            RabbitMessagePublisher publisher,
            MessagingTopology topology,
            Channel channel,
            ReliableMessageHandler handler,
            ReliableMessageProcessor processor,
            Message message) {
    }
}
