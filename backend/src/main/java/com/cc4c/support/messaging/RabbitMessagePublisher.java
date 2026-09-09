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

/** 以 mandatory 发布并等待 correlated confirm，将退回、NACK、超时和连接异常归类。 */
@Component
final class RabbitMessagePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    /**
     * 接入 RabbitTemplate 和确认超时，并启用 mandatory 以识别不可路由消息。
     *
     * @param rabbitTemplate AMQP 消息发送模板
     * @param properties 消息命名空间、密钥及确认重试配置
     */
    RabbitMessagePublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.rabbitTemplate.setMandatory(true);
    }

    /**
     * 发送后等待配置时限内的确认，优先识别退回消息；中断时恢复线程中断标记。
     *
     * @param exchange 发布目标交换机，空字符串表示默认交换机
     * @param routingKey 目标路由键或默认交换机下的队列名
     * @param message 当前 AMQP 消息及其属性
     * @param correlationId 本次发布确认使用的关联标识
     * @return 确认接收或带失败码的发布结果
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
