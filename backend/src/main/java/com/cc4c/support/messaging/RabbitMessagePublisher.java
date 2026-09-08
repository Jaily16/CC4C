package com.cc4c.support.messaging;

import com.cc4c.config.MessagingProperties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 向外部基础设施发布共享基础设施消息，并保留当前投递失败语义。
 */
@Component
final class RabbitMessagePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    /**
     * 创建 RabbitMessagePublisher 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param rabbitTemplate 调用方提供的 {@code rabbitTemplate} 值
     * @param properties 由容器注入的 MessagingProperties 协作组件
     */
    RabbitMessagePublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.rabbitTemplate.setMandatory(true);
    }

    /**
     * 发布可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param exchange 调用方提供的 {@code exchange} 值
     * @param routingKey 调用方提供的 {@code routingKey} 值
     * @param message 当前处理的消息或用户提示
     * @param correlationId 目标对象的稳定标识
     * @return 当前操作产生的 PublishOutcome 结果
     */
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
