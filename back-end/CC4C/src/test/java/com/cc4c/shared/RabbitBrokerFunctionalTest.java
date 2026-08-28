package com.cc4c.shared;

import com.cc4c.functional.FunctionalTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitBrokerFunctionalTest extends FunctionalTestSupport {

    @Autowired
    private RabbitMessagePublisher publisher;

    @Autowired
    private MessagingTopology topology;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Test
    void realTestVhostConfirmsDurableRoutedMessageAndReturnsUnroutableMessage() {
        String id = UUID.randomUUID().toString();
        Message message = MessageBuilder.withBody("test-envelope".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(id)
                .build();

        PublishOutcome routed = publisher.publish(
                topology.eventExchange(), AsyncEventTypes.BLOG_SUBMITTED, message, id + ":routed");
        assertTrue(routed.accepted());

        PublishOutcome returned = publisher.publish(
                topology.eventExchange(), "not.a.configured.route", message, id + ":returned");
        assertEquals("UNROUTABLE", returned.errorCode());
    }

    @Test
    void retryTtlReturnsToMainQueueAndConfirmedDeadPublishReachesFinalDlq() {
        String id = UUID.randomUUID().toString();
        Message message = persistentMessage(id);

        PublishOutcome retry = publisher.publish(
                "",
                topology.retryQueues(AsyncEventTypes.BLOG_REVIEWED).getFirst(),
                message,
                id + ":retry");
        assertTrue(retry.accepted());
        Message retried = receiveEventually(topology.blogReviewedQueue());
        assertEquals(id, retried.getMessageProperties().getMessageId());
        assertEquals(
                MessageDeliveryMode.PERSISTENT,
                retried.getMessageProperties().getReceivedDeliveryMode());

        PublishOutcome dead = publisher.publish(
                topology.deadExchange(),
                AsyncEventTypes.BLOG_REVIEWED + ".dead",
                message,
                id + ":dead");
        assertTrue(dead.accepted());
        Message deadLetter = receiveEventually(topology.deadQueue());
        assertEquals(id, deadLetter.getMessageProperties().getMessageId());
    }

    @Test
    void publisherReconnectsAfterConnectionReset() {
        connectionFactory.resetConnection();
        String id = UUID.randomUUID().toString();
        PublishOutcome outcome = publisher.publish(
                topology.eventExchange(),
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED,
                persistentMessage(id),
                id + ":reconnect");

        assertTrue(outcome.accepted());
        assertEquals(id, receiveEventually(topology.verificationQueue())
                .getMessageProperties().getMessageId());
    }

    private Message persistentMessage(String id) {
        return MessageBuilder.withBody("test-envelope".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(id)
                .build();
    }

    private Message receiveEventually(String queue) {
        for (int attempt = 0; attempt < 12; attempt++) {
            Message message = rabbitTemplate.receive(queue, 250);
            if (message != null) {
                return message;
            }
        }
        throw new AssertionError("Expected RabbitMQ message was not received from the test namespace");
    }
}
