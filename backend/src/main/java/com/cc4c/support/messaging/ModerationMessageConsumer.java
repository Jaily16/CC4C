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

/** 消费博客提交与审核结果通知，按固定载荷类型发信，确认与重试由可靠处理器负责。 */
@Component
class ModerationMessageConsumer {
    private final ReliableMessageProcessor processor;
    private final ObjectMapper objectMapper;
    private final OutboundMailSender mailSender;

    /**
     * 接入可靠消息处理、载荷 JSON 映射与邮件发送服务。
     *
     * @param processor 负责解密、幂等、重试及确认的处理器
     * @param objectMapper 显式信封或载荷 JSON 映射器
     * @param mailSender 带失败分类的文本邮件发送器
     */
    ModerationMessageConsumer(
            ReliableMessageProcessor processor, ObjectMapper objectMapper, OutboundMailSender mailSender) {
        this.processor = processor;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
    }

    /**
     * 使用博客提交消费者身份处理待审核邮件事件。
     *
     * @param message 当前 AMQP 消息及其属性
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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
     * 使用博客审核消费者身份处理审核结果邮件事件。
     *
     * @param message 当前 AMQP 消息及其属性
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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
     * 解析提交载荷并检查收件人，发送包含标题和博客 ID 的待审核通知。
     *
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param plaintext 解密后的敏感载荷字节，不得记录
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
     * 解析审核载荷并检查收件人，按通过或拒绝结果选择邮件标题及正文。
     *
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param plaintext 解密后的敏感载荷字节，不得记录
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
     * 收件邮箱为空时抛出不可重试的 RECIPIENT_UNAVAILABLE 邮件异常。
     *
     * @param recipient 邮件收件地址
     */
    private void requireRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new MailDeliveryException("RECIPIENT_UNAVAILABLE", true, null);
        }
    }

    /**
     * 按明确的通知类型解析解密 JSON，解析失败转换为 INVALID_PAYLOAD。
     *
     * @param <T> 通知载荷类型
     * @param plaintext 解密后的敏感载荷字节，不得记录
     * @param type 明确指定的通知载荷 Java 类型
     * @return 指定类型的博客通知载荷
     */
    private <T> T read(byte[] plaintext, Class<T> type) {
        try {
            return objectMapper.readValue(plaintext, type);
        } catch (IOException exception) {
            throw new MessagePayloadException("INVALID_PAYLOAD", "Blog notification payload cannot be read", exception);
        }
    }
}
