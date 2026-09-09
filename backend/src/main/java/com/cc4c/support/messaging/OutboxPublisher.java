package com.cc4c.support.messaging;

import com.cc4c.common.CorrelationIds;
import com.cc4c.entity.OutboxMessage;
import com.cc4c.repository.OutboxRepository;
import com.cc4c.support.monitoring.Cc4cMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

/** 定时领取 Outbox 发布租约，发送持久消息并按 broker 确认结果推进发布状态或安排退避。 */
@Component
@ConditionalOnProperty(prefix = "cc4c.messaging", name = "dispatcher-enabled", havingValue = "true")
final class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(2));

    private final OutboxRepository repository;
    private final RabbitMessagePublisher publisher;
    private final MessagingTopology topology;
    private final ObjectMapper objectMapper;
    private final Cc4cMetrics metrics;
    private final String workerId = "publisher-" + UUID.randomUUID();

    /**
     * 接入 Outbox、确认发布器、拓扑及 JSON 映射，并保存发布指标。
     *
     * @param repository Outbox 持久化仓库
     * @param publisher 等待 broker 确认的消息发布器
     * @param topology 消息队列与交换机名称规则
     * @param objectMapper 显式信封或载荷 JSON 映射器
     * @param metrics 发布消费指标记录器
     */
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

    /**
     * 委托主构造器并使用禁用指标实现。
     *
     * @param repository Outbox 持久化仓库
     * @param publisher 等待 broker 确认的消息发布器
     * @param topology 消息队列与交换机名称规则
     * @param objectMapper 显式信封或载荷 JSON 映射器
     */
    OutboxPublisher(
            OutboxRepository repository,
            RabbitMessagePublisher publisher,
            MessagingTopology topology,
            ObjectMapper objectMapper) {
        this(repository, publisher, topology, objectMapper, Cc4cMetrics.disabled());
    }

    /** 每轮最多领取 50 条、租约 30 秒并逐条发布；领取失败只记录类型并结束本轮。 */
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

    /**
     * 构造持久 JSON 信封并等待发布确认；不可路由最多三次，其他发布失败最多八次，按固定退避安排重试。
     *
     * @param outbox 已领取发布租约的 Outbox 记录
     */
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
                    .setHeader(
                            CorrelationIds.AMQP_HEADER,
                            CorrelationIds.normalize(outbox.correlationId(), outbox.eventId()))
                    .setHeader("cc4c-retry-attempt", 0)
                    .build();
        } catch (JsonProcessingException exception) {
            repository.markPublishFailure(
                    outbox.eventId(), outbox.generation(), "ENVELOPE_SERIALIZATION_FAILED", Instant.now(), true);
            metrics.record(
                    "cc4c.messaging.publish.duration",
                    startedNanos,
                    "event_type",
                    outbox.eventType(),
                    "outcome",
                    "serialization_error");
            return;
        }

        PublishOutcome outcome = publisher.publish(
                topology.eventExchange(), outbox.routingKey(), message, outbox.eventId() + ":" + outbox.generation());
        if (outcome.accepted()) {
            repository.markPublished(outbox.eventId(), outbox.generation());
            metrics.record(
                    "cc4c.messaging.publish.duration",
                    startedNanos,
                    "event_type",
                    outbox.eventType(),
                    "outcome",
                    "confirmed");
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
                outbox.eventId(),
                outbox.generation(),
                outcome.errorCode(),
                Instant.now().plus(delay),
                terminal);
        metrics.record(
                "cc4c.messaging.publish.duration",
                startedNanos,
                "event_type",
                outbox.eventType(),
                "outcome",
                terminal ? "failed" : "retry");
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
