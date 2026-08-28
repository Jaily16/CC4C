package com.cc4c.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "cc4c.messaging", name = "dispatcher-enabled", havingValue = "true")
final class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30),
            Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30),
            Duration.ofHours(1), Duration.ofHours(2));

    private final OutboxRepository repository;
    private final RabbitMessagePublisher publisher;
    private final MessagingTopology topology;
    private final ObjectMapper objectMapper;
    private final Cc4cMetrics metrics;
    private final String workerId = "publisher-" + UUID.randomUUID();

    @Autowired
    OutboxPublisher(
            OutboxRepository repository,
            RabbitMessagePublisher publisher,
            MessagingTopology topology,
            ObjectMapper objectMapper,
            Cc4cMetrics metrics) {
        this.repository = repository;
        this.publisher = publisher;
        this.topology = topology;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    OutboxPublisher(
            OutboxRepository repository,
            RabbitMessagePublisher publisher,
            MessagingTopology topology,
            ObjectMapper objectMapper) {
        this(repository, publisher, topology, objectMapper, Cc4cMetrics.disabled());
    }

    @Scheduled(fixedDelayString = "${cc4c.messaging.poll-interval:500ms}")
    void dispatch() {
        List<OutboxMessage> messages;
        try {
            messages = repository.claimBatch(workerId, 50, Instant.now().plusSeconds(30));
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "outbox_claim")
                    .addKeyValue("result", "failed")
                    .addKeyValue("exception_type", exception.getClass().getSimpleName())
                    .log("Outbox claim failed");
            return;
        }
        messages.forEach(this::publish);
    }

    private void publish(OutboxMessage outbox) {
        long startedNanos = metrics.start();
        Message message;
        try {
            message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(outbox.envelope()))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(outbox.eventId() + ":" + outbox.generation())
                    .setHeader("cc4c-event-type", outbox.eventType())
                    .setHeader(CorrelationIds.AMQP_HEADER,
                            CorrelationIds.normalize(outbox.correlationId(), outbox.eventId()))
                    .setHeader("cc4c-retry-attempt", 0)
                    .build();
        } catch (JsonProcessingException exception) {
            repository.markPublishFailure(
                    outbox.eventId(), outbox.generation(), "ENVELOPE_SERIALIZATION_FAILED",
                    Instant.now(), true);
            metrics.record("cc4c.messaging.publish.duration", startedNanos,
                    "event_type", outbox.eventType(), "outcome", "serialization_error");
            return;
        }

        PublishOutcome outcome = publisher.publish(
                topology.eventExchange(), outbox.routingKey(), message,
                outbox.eventId() + ":" + outbox.generation());
        if (outcome.accepted()) {
            repository.markPublished(outbox.eventId(), outbox.generation());
            metrics.record("cc4c.messaging.publish.duration", startedNanos,
                    "event_type", outbox.eventType(), "outcome", "confirmed");
            log.atInfo()
                    .addKeyValue("event", "message_publish")
                    .addKeyValue("event_id", outbox.eventId())
                    .addKeyValue("event_type", outbox.eventType())
                    .addKeyValue("generation", outbox.generation())
                    .addKeyValue("result", "confirmed")
                    .log("Message publish completed");
            return;
        }

        int nextAttempt = outbox.publishAttempts() + 1;
        int limit = "UNROUTABLE".equals(outcome.errorCode()) ? 3 : BACKOFF.size();
        boolean terminal = nextAttempt >= limit;
        Duration delay = BACKOFF.get(Math.min(nextAttempt - 1, BACKOFF.size() - 1));
        repository.markPublishFailure(
                outbox.eventId(), outbox.generation(), outcome.errorCode(),
                Instant.now().plus(delay), terminal);
        metrics.record("cc4c.messaging.publish.duration", startedNanos,
                "event_type", outbox.eventType(), "outcome", terminal ? "failed" : "retry");
        log.atWarn()
                .addKeyValue("event", "message_publish")
                .addKeyValue("event_id", outbox.eventId())
                .addKeyValue("event_type", outbox.eventType())
                .addKeyValue("generation", outbox.generation())
                .addKeyValue("attempt", nextAttempt)
                .addKeyValue("result", terminal ? "failed" : "retry_scheduled")
                .log("Message publish did not complete");
    }
}
