package com.cc4c.support.messaging;

import com.cc4c.common.MailDeliveryException;
import com.cc4c.common.MessagePayloadException;
import com.cc4c.support.OutboundMailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 消费并处理审核与消息运维消息，遵循既有幂等与重试边界。
 */
@Component
class ModerationMessageConsumer {
    private final ReliableMessageProcessor processor;
    private final ObjectMapper objectMapper;
    private final OutboundMailSender mailSender;

    /**
     * 创建 ModerationMessageConsumer 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param processor 调用方提供的 {@code processor} 值
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param mailSender 调用方提供的 {@code mailSender} 值
     */
    ModerationMessageConsumer(
            ReliableMessageProcessor processor, ObjectMapper objectMapper, OutboundMailSender mailSender) {
        this.processor = processor;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
    }

    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param message 当前处理的消息或用户提示
     * @param channel 调用方提供的 {@code channel} 值
     * @param deliveryTag 调用方提供的 {@code deliveryTag} 值
     * @throws IOException 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
    @RabbitListener(
            queues = "#{@messagingTopology.blogSubmittedQueue()}",
            autoStartup = "${cc4c.messaging.consumers-enabled:true}")
    void submitted(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        processor.process(
                "moderation-blog-submitted-v1",
                AsyncEventTypes.BLOG_SUBMITTED,
                message,
                channel,
                deliveryTag,
                (envelope, plaintext) -> sendSubmitted(envelope, plaintext));
    }

    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param message 当前处理的消息或用户提示
     * @param channel 调用方提供的 {@code channel} 值
     * @param deliveryTag 调用方提供的 {@code deliveryTag} 值
     * @throws IOException 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
    @RabbitListener(
            queues = "#{@messagingTopology.blogReviewedQueue()}",
            autoStartup = "${cc4c.messaging.consumers-enabled:true}")
    void reviewed(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        processor.process(
                "moderation-blog-reviewed-v1",
                AsyncEventTypes.BLOG_REVIEWED,
                message,
                channel,
                deliveryTag,
                (envelope, plaintext) -> sendReviewed(envelope, plaintext));
    }

    /**
     * 发布可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param envelope 调用方提供的 {@code envelope} 值
     * @param plaintext 调用方提供的 {@code plaintext} 值
     */
    private void sendSubmitted(MessageEnvelope envelope, byte[] plaintext) {
        BlogSubmittedNotificationV1 payload = read(plaintext, BlogSubmittedNotificationV1.class);
        requireRecipient(payload.recipientEmail());
        mailSender.sendText(
                envelope.eventId(),
                payload.recipientEmail(),
                "CC4C 博客待审核",
                "博客《" + payload.title() + "》（ID：" + payload.blogId() + "）已提交，请进入管理端审核。");
    }

    /**
     * 发布可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param envelope 调用方提供的 {@code envelope} 值
     * @param plaintext 调用方提供的 {@code plaintext} 值
     */
    private void sendReviewed(MessageEnvelope envelope, byte[] plaintext) {
        BlogReviewedNotificationV1 payload = read(plaintext, BlogReviewedNotificationV1.class);
        requireRecipient(payload.recipientEmail());
        boolean approved = payload.outcome() == BlogReviewedNotificationV1.ReviewOutcome.APPROVED;
        mailSender.sendText(
                envelope.eventId(),
                payload.recipientEmail(),
                approved ? "CC4C 博客审核通过" : "CC4C 博客审核结果",
                "您的博客《" + payload.title() + "》（ID：" + payload.blogId() + "）" + (approved ? "已通过审核。" : "未通过本次审核。"));
    }

    /**
     * 校验可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param recipient 调用方提供的 {@code recipient} 值
     */
    private void requireRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new MailDeliveryException("RECIPIENT_UNAVAILABLE", true, null);
        }
    }

    /**
     * 读取可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param <T> 方法使用的类型参数
     * @param plaintext 调用方提供的 {@code plaintext} 值
     * @param type 调用方提供的 {@code type} 值
     * @return 当前操作产生的 T 结果
     */
    private <T> T read(byte[] plaintext, Class<T> type) {
        try {
            return objectMapper.readValue(plaintext, type);
        } catch (IOException exception) {
            throw new MessagePayloadException("INVALID_PAYLOAD", "Blog notification payload cannot be read", exception);
        }
    }
}
