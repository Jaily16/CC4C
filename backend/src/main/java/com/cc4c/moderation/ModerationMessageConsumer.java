package com.cc4c.moderation;

import com.cc4c.community.api.BlogReviewedNotificationV1;
import com.cc4c.community.api.BlogSubmittedNotificationV1;
import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.MailDeliveryException;
import com.cc4c.shared.MessageEnvelope;
import com.cc4c.shared.MessagePayloadException;
import com.cc4c.shared.OutboundMailSender;
import com.cc4c.shared.ReliableMessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
class ModerationMessageConsumer {
    private final ReliableMessageProcessor processor;
    private final ObjectMapper objectMapper;
    private final OutboundMailSender mailSender;

    ModerationMessageConsumer(
            ReliableMessageProcessor processor, ObjectMapper objectMapper, OutboundMailSender mailSender) {
        this.processor = processor;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
    }

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

    private void sendSubmitted(MessageEnvelope envelope, byte[] plaintext) {
        BlogSubmittedNotificationV1 payload = read(plaintext, BlogSubmittedNotificationV1.class);
        requireRecipient(payload.recipientEmail());
        mailSender.sendText(
                envelope.eventId(),
                payload.recipientEmail(),
                "CC4C 博客待审核",
                "博客《" + payload.title() + "》（ID：" + payload.blogId() + "）已提交，请进入管理端审核。");
    }

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

    private void requireRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new MailDeliveryException("RECIPIENT_UNAVAILABLE", true, null);
        }
    }

    private <T> T read(byte[] plaintext, Class<T> type) {
        try {
            return objectMapper.readValue(plaintext, type);
        } catch (IOException exception) {
            throw new MessagePayloadException("INVALID_PAYLOAD", "Blog notification payload cannot be read", exception);
        }
    }
}
