package com.cc4c.identity.internal;

import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.MessageEnvelope;
import com.cc4c.shared.OutboundMailSender;
import com.cc4c.shared.ReliableMessageHandler;
import com.cc4c.shared.ReliableMessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityMessageConsumerTest {

    @Test
    void verificationEmailActivatesCodeImmediatelyBeforeSending() throws Exception {
        Fixture fixture = fixture();
        when(fixture.codes.activateForDelivery(
                eq("recipient@example.com"), eq(VerificationPurpose.REGISTER), eq("246810"),
                eq("event-1"), any(), any())).thenReturn(true);

        ReliableMessageHandler handler = captureHandler(fixture);
        handler.handle(fixture.envelope, fixture.plaintext);

        verify(fixture.codes).activateForDelivery(
                eq("recipient@example.com"), eq(VerificationPurpose.REGISTER), eq("246810"),
                eq("event-1"), any(), any());
        verify(fixture.mail).sendText(
                eq("event-1"), eq("recipient@example.com"), eq("CC4C 邮箱验证码"),
                eq("您的 CC4C 验证码是 246810，10 分钟内有效。"));
    }

    @Test
    void delayedOlderVerificationEventDoesNotSendWhenActivationIsRejected() throws Exception {
        Fixture fixture = fixture();
        when(fixture.codes.activateForDelivery(any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        captureHandler(fixture).handle(fixture.envelope, fixture.plaintext);

        verify(fixture.mail, never()).sendText(any(), any(), any(), any());
    }

    @Test
    void expiredAndDeadEventsDiscardOnlyTheirMatchingVerificationRecord() throws Exception {
        Fixture fixture = fixture();
        ReliableMessageHandler handler = captureHandler(fixture);

        handler.expired(fixture.envelope, fixture.plaintext);
        handler.dead(fixture.envelope, fixture.plaintext, "MAIL_SMTP_PERMANENT");

        verify(fixture.codes, org.mockito.Mockito.times(2)).discardIfCurrent(
                "recipient@example.com", VerificationPurpose.REGISTER, "event-1");
        verify(fixture.mail, never()).sendText(any(), any(), any(), any());
    }

    private ReliableMessageHandler captureHandler(Fixture fixture) throws Exception {
        fixture.consumer.verificationEmail(fixture.message, fixture.channel, 7L);
        ArgumentCaptor<ReliableMessageHandler> handler =
                ArgumentCaptor.forClass(ReliableMessageHandler.class);
        verify(fixture.processor).process(
                eq("identity-verification-mail-v1"),
                eq(AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED),
                eq(fixture.message), eq(fixture.channel), eq(7L), handler.capture());
        return handler.getValue();
    }

    private Fixture fixture() throws Exception {
        ReliableMessageProcessor processor = mock(ReliableMessageProcessor.class);
        VerificationCodeService codes = mock(VerificationCodeService.class);
        OutboundMailSender mail = mock(OutboundMailSender.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        IdentityMessageConsumer consumer = new IdentityMessageConsumer(
                processor, objectMapper, codes, mail);
        Channel channel = mock(Channel.class);
        Message message = new Message(new byte[0]);
        Instant occurredAt = Instant.parse("2026-08-28T00:00:00Z");
        MessageEnvelope envelope = new MessageEnvelope(
                "event-1", AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED, 1, 0,
                occurredAt, occurredAt.plusSeconds(600), "test-v1", new byte[12], new byte[]{1});
        byte[] plaintext = objectMapper.writeValueAsBytes(new VerificationEmailRequestedV1(
                "recipient@example.com", VerificationPurpose.REGISTER, "246810"));
        return new Fixture(processor, codes, mail, consumer, channel, message, envelope, plaintext);
    }

    private record Fixture(
            ReliableMessageProcessor processor,
            VerificationCodeService codes,
            OutboundMailSender mail,
            IdentityMessageConsumer consumer,
            Channel channel,
            Message message,
            MessageEnvelope envelope,
            byte[] plaintext) {
    }
}
