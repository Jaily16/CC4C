package com.cc4c.identity.internal;

import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.MessageEnvelope;
import com.cc4c.shared.OutboundMailSender;
import com.cc4c.shared.ReliableMessageHandler;
import com.cc4c.shared.ReliableMessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 消费并处理身份认证消息，遵循既有幂等与重试边界。
 */
@Component
class IdentityMessageConsumer {
    private static final String CONSUMER = "identity-verification-mail-v1";

    private final ReliableMessageProcessor processor;
    private final ObjectMapper objectMapper;
    private final VerificationCodeService verificationCodes;
    private final OutboundMailSender mailSender;

    /**
     * 创建 IdentityMessageConsumer 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param processor 调用方提供的 {@code processor} 值
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param verificationCodes 由容器注入的 VerificationCodeService 协作组件
     * @param mailSender 调用方提供的 {@code mailSender} 值
     */
    IdentityMessageConsumer(
            ReliableMessageProcessor processor,
            ObjectMapper objectMapper,
            VerificationCodeService verificationCodes,
            OutboundMailSender mailSender) {
        this.processor = processor;
        this.objectMapper = objectMapper;
        this.verificationCodes = verificationCodes;
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
            queues = "#{@messagingTopology.verificationQueue()}",
            autoStartup = "${cc4c.messaging.consumers-enabled:true}")
    void verificationEmail(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        processor.process(
                CONSUMER,
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED,
                message,
                channel,
                deliveryTag,
                new VerificationHandler());
    }

    /**
     * 消费并处理身份认证消息，遵循既有幂等与重试边界。
     */
    private final class VerificationHandler implements ReliableMessageHandler {
        /**
         * 处理当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @param envelope 调用方提供的 {@code envelope} 值
         * @param plaintext 调用方提供的 {@code plaintext} 值
         */
        @Override
        public void handle(MessageEnvelope envelope, byte[] plaintext) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            boolean activated = verificationCodes.activateForDelivery(
                    payload.recipientEmail(),
                    payload.purpose(),
                    payload.verificationCode(),
                    envelope.eventId(),
                    envelope.occurredAt(),
                    envelope.expiresAt());
            if (!activated) {
                return;
            }
            mailSender.sendText(
                    envelope.eventId(),
                    payload.recipientEmail(),
                    "CC4C 邮箱验证码",
                    "您的 CC4C 验证码是 " + payload.verificationCode() + "，10 分钟内有效。");
        }

        /**
         * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @param envelope 调用方提供的 {@code envelope} 值
         * @param plaintext 调用方提供的 {@code plaintext} 值
         */
        @Override
        public void expired(MessageEnvelope envelope, byte[] plaintext) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            verificationCodes.discardIfCurrent(payload.recipientEmail(), payload.purpose(), envelope.eventId());
        }

        /**
         * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @param envelope 调用方提供的 {@code envelope} 值
         * @param plaintext 调用方提供的 {@code plaintext} 值
         * @param errorCode 调用方提供的 {@code errorCode} 值
         */
        @Override
        public void dead(MessageEnvelope envelope, byte[] plaintext, String errorCode) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            verificationCodes.discardIfCurrent(payload.recipientEmail(), payload.purpose(), envelope.eventId());
        }

        /**
         * 读取当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @param plaintext 调用方提供的 {@code plaintext} 值
         * @return 当前操作产生的 VerificationEmailRequestedV1 结果
         */
        private VerificationEmailRequestedV1 read(byte[] plaintext) {
            try {
                return objectMapper.readValue(plaintext, VerificationEmailRequestedV1.class);
            } catch (IOException exception) {
                throw new com.cc4c.shared.MessagePayloadException(
                        "INVALID_PAYLOAD", "Verification payload cannot be read", exception);
            }
        }
    }
}
