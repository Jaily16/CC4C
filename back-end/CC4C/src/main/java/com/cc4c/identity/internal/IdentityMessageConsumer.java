package com.cc4c.identity.internal;

import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.MessageEnvelope;
import com.cc4c.shared.OutboundMailSender;
import com.cc4c.shared.ReliableMessageHandler;
import com.cc4c.shared.ReliableMessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
class IdentityMessageConsumer {
    private static final String CONSUMER = "identity-verification-mail-v1";

    private final ReliableMessageProcessor processor;
    private final ObjectMapper objectMapper;
    private final VerificationCodeService verificationCodes;
    private final OutboundMailSender mailSender;

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

    @RabbitListener(
            queues = "#{@messagingTopology.verificationQueue()}",
            autoStartup = "${cc4c.messaging.consumers-enabled:true}")
    void verificationEmail(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        processor.process(
                CONSUMER,
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED,
                message,
                channel,
                deliveryTag,
                new VerificationHandler());
    }

    private final class VerificationHandler implements ReliableMessageHandler {
        @Override
        public void handle(MessageEnvelope envelope, byte[] plaintext) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            boolean activated = verificationCodes.activateForDelivery(
                    payload.recipientEmail(), payload.purpose(), payload.verificationCode(),
                    envelope.eventId(), envelope.occurredAt(), envelope.expiresAt());
            if (!activated) {
                return;
            }
            mailSender.sendText(
                    envelope.eventId(),
                    payload.recipientEmail(),
                    "CC4C 邮箱验证码",
                    "您的 CC4C 验证码是 " + payload.verificationCode() + "，10 分钟内有效。");
        }

        @Override
        public void expired(MessageEnvelope envelope, byte[] plaintext) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            verificationCodes.discardIfCurrent(
                    payload.recipientEmail(), payload.purpose(), envelope.eventId());
        }

        @Override
        public void dead(MessageEnvelope envelope, byte[] plaintext, String errorCode) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            verificationCodes.discardIfCurrent(
                    payload.recipientEmail(), payload.purpose(), envelope.eventId());
        }

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
