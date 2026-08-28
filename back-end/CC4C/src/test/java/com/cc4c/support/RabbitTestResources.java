package com.cc4c.support;

import com.cc4c.shared.MessagingTopology;
import org.springframework.amqp.core.AmqpAdmin;

public final class RabbitTestResources {
    private RabbitTestResources() {
    }

    public static void deleteKnownNamespaceResources(
            AmqpAdmin admin, MessagingTopology topology) {
        topology.mainQueuesByEventType().forEach((eventType, queueName) -> {
            topology.retryQueues(eventType).forEach(admin::deleteQueue);
            admin.deleteQueue(queueName);
        });
        admin.deleteQueue(topology.deadQueue());
        admin.deleteExchange(topology.deadExchange());
        admin.deleteExchange(topology.eventExchange());
    }
}
