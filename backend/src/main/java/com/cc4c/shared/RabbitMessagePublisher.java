package com.cc4c.shared;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
final class RabbitMessagePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    RabbitMessagePublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.rabbitTemplate.setMandatory(true);
    }

    PublishOutcome publish(String exchange, String routingKey, Message message, String correlationId) {
        CorrelationData correlationData = new CorrelationData(correlationId);
        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
            CorrelationData.Confirm confirm =
                    correlationData.getFuture().get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (correlationData.getReturned() != null) {
                return PublishOutcome.failed("UNROUTABLE");
            }
            if (!confirm.isAck()) {
                return PublishOutcome.failed("BROKER_NACK");
            }
            return PublishOutcome.confirmed();
        } catch (TimeoutException exception) {
            return PublishOutcome.failed("CONFIRM_TIMEOUT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PublishOutcome.failed("PUBLISH_INTERRUPTED");
        } catch (ExecutionException | AmqpException exception) {
            return PublishOutcome.failed("BROKER_UNAVAILABLE");
        }
    }
}
