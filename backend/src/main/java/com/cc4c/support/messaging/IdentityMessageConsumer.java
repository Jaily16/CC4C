package com.cc4c.support.messaging;

import com.cc4c.service.VerificationCodeService;
import com.cc4c.support.OutboundMailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** 消费验证码邮件事件，将解密、幂等和 ACK/NACK 交给可靠消息处理器。 */
@Component
class IdentityMessageConsumer {
    private static final String CONSUMER = "identity-verification-mail-v1";

    private final ReliableMessageProcessor processor;
    private final ObjectMapper objectMapper;
    private final VerificationCodeService verificationCodes;
    private final OutboundMailSender mailSender;

    /**
     * 接入可靠处理器、载荷 JSON 映射、验证码激活和邮件发送服务。
     *
     * @param processor 负责解密、幂等、重试及确认的处理器
     * @param objectMapper 显式信封或载荷 JSON 映射器
     * @param verificationCodes 验证码激活和条件撤销服务
     * @param mailSender 带失败分类的文本邮件发送器
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
     * 将验证码队列投递交给固定消费者身份和验证码处理回调。
     *
     * @param message 当前 AMQP 消息及其属性
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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

    /** 在实际发信前激活验证码，并在过期或死信时仅撤销对应当前签发。 */
    private final class VerificationHandler implements ReliableMessageHandler {
        /**
         * 解析验证码载荷并尝试激活；未激活则不发信，激活后通过邮件服务发送验证码。
         *
         * @param envelope 包含事件元数据和加密载荷的信封
         * @param plaintext 解密后的敏感载荷字节，不得记录
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
         * 解析过期载荷，仅当其事件仍为当前签发时删除 Redis 验证码。
         *
         * @param envelope 包含事件元数据和加密载荷的信封
         * @param plaintext 解密后的敏感载荷字节，不得记录
         */
        @Override
        public void expired(MessageEnvelope envelope, byte[] plaintext) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            verificationCodes.discardIfCurrent(payload.recipientEmail(), payload.purpose(), envelope.eventId());
        }

        /**
         * 解析终止载荷并有条件撤销当前验证码，不覆盖较新的签发。
         *
         * @param envelope 包含事件元数据和加密载荷的信封
         * @param plaintext 解密后的敏感载荷字节，不得记录
         * @param errorCode 不含敏感正文的失败分类码
         */
        @Override
        public void dead(MessageEnvelope envelope, byte[] plaintext, String errorCode) {
            VerificationEmailRequestedV1 payload = read(plaintext);
            verificationCodes.discardIfCurrent(payload.recipientEmail(), payload.purpose(), envelope.eventId());
        }

        /**
         * 按固定验证码载荷类型读取明文 JSON，解析失败转换为 INVALID_PAYLOAD。
         *
         * @param plaintext 解密后的敏感载荷字节，不得记录
         * @return 验证码邮件载荷
         */
        private VerificationEmailRequestedV1 read(byte[] plaintext) {
            try {
                return objectMapper.readValue(plaintext, VerificationEmailRequestedV1.class);
            } catch (IOException exception) {
                throw new com.cc4c.common.MessagePayloadException(
                        "INVALID_PAYLOAD", "Verification payload cannot be read", exception);
            }
        }
    }
}
