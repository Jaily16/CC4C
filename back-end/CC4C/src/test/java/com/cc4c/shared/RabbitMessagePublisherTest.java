package com.cc4c.shared;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class RabbitMessagePublisherTest {

    @Test
    void requiresAckAndNoReturnedMessage() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("events"), eq("route"), any(Message.class), any(CorrelationData.class));

        PublishOutcome outcome = publisher(rabbit, Duration.ofSeconds(1))
                .publish("events", "route", new Message(new byte[0]), "event:0");

        assertTrue(outcome.accepted());
    }

    @Test
    void ackWithReturnIsUnroutableAndNackIsNotAccepted() {
        RabbitTemplate returnedRabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0]), 312, "NO_ROUTE", "events", "missing"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(returnedRabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
        PublishOutcome returned = publisher(returnedRabbit, Duration.ofSeconds(1))
                .publish("events", "missing", new Message(new byte[0]), "event:0");
        assertFalse(returned.accepted());
        assertEquals("UNROUTABLE", returned.errorCode());

        RabbitTemplate nackedRabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(nackedRabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
        PublishOutcome nacked = publisher(nackedRabbit, Duration.ofSeconds(1))
                .publish("events", "route", new Message(new byte[0]), "event:0");
        assertEquals("BROKER_NACK", nacked.errorCode());
    }

    @Test
    void confirmTimeoutAndConnectionFailureRemainRetryable() {
        RabbitTemplate silentRabbit = mock(RabbitTemplate.class);
        PublishOutcome timeout = publisher(silentRabbit, Duration.ofMillis(1))
                .publish("events", "route", new Message(new byte[0]), "event:0");
        assertEquals("CONFIRM_TIMEOUT", timeout.errorCode());

        RabbitTemplate failedRabbit = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new IllegalStateException("unavailable")))
                .when(failedRabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
        PublishOutcome unavailable = publisher(failedRabbit, Duration.ofSeconds(1))
                .publish("events", "route", new Message(new byte[0]), "event:0");
        assertEquals("BROKER_UNAVAILABLE", unavailable.errorCode());
    }

    private RabbitMessagePublisher publisher(RabbitTemplate rabbit, Duration timeout) {
        MessagingProperties properties = new MessagingProperties(
                "cc4c.test.messaging",
                "test-v1",
                "test-v1=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "reviewer@example.com",
                timeout,
                List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)),
                Duration.ofMillis(500),
                false,
                false);
        return new RabbitMessagePublisher(rabbit, properties);
    }
}
