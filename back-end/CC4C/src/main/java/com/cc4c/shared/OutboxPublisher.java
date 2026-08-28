package com.cc4c.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
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
    private final String workerId = "publisher-" + UUID.randomUUID();

    OutboxPublisher(
            OutboxRepository repository,
            RabbitMessagePublisher publisher,
            MessagingTopology topology,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.topology = topology;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${cc4c.messaging.poll-interval:500ms}")
    void dispatch() {
        List<OutboxMessage> messages;
        try {
            messages = repository.claimBatch(workerId, 50, Instant.now().plusSeconds(30));
        } catch (RuntimeException exception) {
            log.warn("messaging_action=claim result=failed error={}", exception.getClass().getSimpleName());
            return;
        }
        messages.forEach(this::publish);
    }

    private void publish(OutboxMessage outbox) {
        Message message;
        try {
            message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(outbox.envelope()))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(outbox.eventId() + ":" + outbox.generation())
                    .setHeader("cc4c-event-type", outbox.eventType())
                    .setHeader("cc4c-retry-attempt", 0)
                    .build();
        } catch (JsonProcessingException exception) {
            repository.markPublishFailure(
                    outbox.eventId(), outbox.generation(), "ENVELOPE_SERIALIZATION_FAILED",
                    Instant.now(), true);
            return;
        }

        PublishOutcome outcome = publisher.publish(
                topology.eventExchange(), outbox.routingKey(), message,
                outbox.eventId() + ":" + outbox.generation());
        if (outcome.accepted()) {
            repository.markPublished(outbox.eventId(), outbox.generation());
            log.info(
                    "messaging_action=publish event={} type={} generation={} result=confirmed",
                    outbox.eventId(), outbox.eventType(), outbox.generation());
            return;
        }

        int nextAttempt = outbox.publishAttempts() + 1;
        int limit = "UNROUTABLE".equals(outcome.errorCode()) ? 3 : BACKOFF.size();
        boolean terminal = nextAttempt >= limit;
        Duration delay = BACKOFF.get(Math.min(nextAttempt - 1, BACKOFF.size() - 1));
        repository.markPublishFailure(
                outbox.eventId(), outbox.generation(), outcome.errorCode(),
                Instant.now().plus(delay), terminal);
        log.warn(
                "messaging_action=publish event={} type={} generation={} attempt={} result={}",
                outbox.eventId(), outbox.eventType(), outbox.generation(), nextAttempt,
                terminal ? "failed" : "retry_scheduled");
    }
}
