package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitMessagingConfigurationTest {

    @Test
    void declaresVersionedDurableQuorumTopologyWithFiniteRetryAndDeadLettering() {
        MessagingProperties properties = new MessagingProperties(
                "cc4c.test.messaging",
                "test-v1",
                "test-v1=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "reviewer@example.com",
                Duration.ofSeconds(5),
                List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                Duration.ofMillis(500),
                false,
                false);
        MessagingTopology topology = new MessagingTopology(properties);
        Declarables declarables = new RabbitMessagingConfiguration().messagingDeclarables(topology);

        List<Queue> queues = declarables.getDeclarablesByType(Queue.class);
        assertEquals(13, queues.size());
        queues.forEach(queue -> {
            assertTrue(queue.isDurable());
            assertEquals("quorum", queue.getArguments().get("x-queue-type"));
            assertEquals("reject-publish", queue.getArguments().get("x-overflow"));
        });
        Queue verification = queue(queues, topology.verificationQueue());
        assertEquals(topology.deadExchange(), verification.getArguments().get("x-dead-letter-exchange"));
        assertEquals("at-least-once", verification.getArguments().get("x-dead-letter-strategy"));
        Queue retry = queue(
                queues,
                topology.retryQueues(AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED)
                        .getFirst());
        assertEquals(30_000L, retry.getArguments().get("x-message-ttl"));
        assertEquals(topology.eventExchange(), retry.getArguments().get("x-dead-letter-exchange"));
        Queue dead = queue(queues, topology.deadQueue());
        assertTrue(!dead.getArguments().containsKey("x-dead-letter-strategy"));

        List<TopicExchange> exchanges = declarables.getDeclarablesByType(TopicExchange.class);
        assertEquals(2, exchanges.size());
        exchanges.forEach(exchange -> assertTrue(exchange.isDurable()));
    }

    private Queue queue(List<Queue> queues, String name) {
        Queue queue = queues.stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElse(null);
        assertNotNull(queue);
        return queue;
    }
}
