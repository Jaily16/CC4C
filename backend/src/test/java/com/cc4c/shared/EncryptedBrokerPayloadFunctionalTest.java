package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cc4c.functional.FunctionalTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class EncryptedBrokerPayloadFunctionalTest extends FunctionalTestSupport {
    private static final String RECIPIENT = "encrypted-broker@example.com";
    private static final String CODE = "246810";

    @Autowired
    private TransactionalOutbox transactionalOutbox;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private RabbitMessagePublisher publisher;

    @Autowired
    private MessagingTopology topology;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin rabbitAdmin;

    @Test
    void dispatchedRabbitEnvelopeContainsNeitherRecipientNorVerificationCode() {
        jdbcTemplate.update("DELETE FROM async_inbox");
        jdbcTemplate.update("DELETE FROM async_outbox");
        rabbitAdmin.purgeQueue(topology.verificationQueue(), false);

        Instant occurredAt = Instant.now().minusSeconds(1);
        String eventId = transactionalOutbox.append(
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED,
                "verification",
                "safe-subject-digest",
                Map.of("recipientEmail", RECIPIENT, "verificationCode", CODE),
                occurredAt,
                occurredAt.plusSeconds(600));
        new OutboxPublisher(outboxRepository, publisher, topology, objectMapper).dispatch();

        Message message = receiveEvent(eventId);
        assertNotNull(message);
        String envelope = new String(message.getBody(), StandardCharsets.UTF_8);
        assertFalse(envelope.contains(RECIPIENT));
        assertFalse(envelope.contains(CODE));
    }

    private Message receiveEvent(String eventId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Message message = rabbitTemplate.receive(topology.verificationQueue(), 250);
            if (message != null
                    && (eventId + ":0").equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return null;
    }
}
