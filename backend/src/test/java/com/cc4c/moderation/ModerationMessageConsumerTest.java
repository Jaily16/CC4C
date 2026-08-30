package com.cc4c.moderation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cc4c.community.api.BlogReviewedNotificationV1;
import com.cc4c.community.api.BlogSubmittedNotificationV1;
import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.MailDeliveryException;
import com.cc4c.shared.MessageEnvelope;
import com.cc4c.shared.OutboundMailSender;
import com.cc4c.shared.ReliableMessageHandler;
import com.cc4c.shared.ReliableMessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;

class ModerationMessageConsumerTest {

    @Test
    void submittedAndReviewedEventsUseVersionedPlainTextTemplatesWithoutBlogBody() throws Exception {
        Fixture fixture = fixture();
        Instant now = Instant.parse("2026-08-28T00:00:00Z");

        ReliableMessageHandler submitted = captureSubmitted(fixture);
        submitted.handle(
                fixture.envelope(AsyncEventTypes.BLOG_SUBMITTED),
                fixture.objectMapper.writeValueAsBytes(
                        new BlogSubmittedNotificationV1("reviewer@example.com", "42", "Safe title", now)));
        verify(fixture.mail)
                .sendText("event-1", "reviewer@example.com", "CC4C 博客待审核", "博客《Safe title》（ID：42）已提交，请进入管理端审核。");

        ReliableMessageHandler reviewed = captureReviewed(fixture);
        reviewed.handle(
                fixture.envelope(AsyncEventTypes.BLOG_REVIEWED),
                fixture.objectMapper.writeValueAsBytes(new BlogReviewedNotificationV1(
                        "author@example.com",
                        "42",
                        "Safe title",
                        BlogReviewedNotificationV1.ReviewOutcome.DENIED,
                        now)));
        verify(fixture.mail)
                .sendText("event-1", "author@example.com", "CC4C 博客审核结果", "您的博客《Safe title》（ID：42）未通过本次审核。");
    }

    @Test
    void missingRecipientIsClassifiedAsPermanentWithoutCallingMailGateway() throws Exception {
        Fixture fixture = fixture();
        ReliableMessageHandler reviewed = captureReviewed(fixture);
        byte[] payload = fixture.objectMapper.writeValueAsBytes(new BlogReviewedNotificationV1(
                "",
                "42",
                "Safe title",
                BlogReviewedNotificationV1.ReviewOutcome.APPROVED,
                Instant.parse("2026-08-28T00:00:00Z")));

        MailDeliveryException failure = assertThrows(
                MailDeliveryException.class,
                () -> reviewed.handle(fixture.envelope(AsyncEventTypes.BLOG_REVIEWED), payload));

        assertTrue(failure.permanent());
        verify(fixture.mail, org.mockito.Mockito.never()).sendText(any(), any(), any(), any());
    }

    private ReliableMessageHandler captureSubmitted(Fixture fixture) throws Exception {
        fixture.consumer.submitted(fixture.message, fixture.channel, 7L);
        return capturedHandler(fixture, "moderation-blog-submitted-v1", AsyncEventTypes.BLOG_SUBMITTED);
    }

    private ReliableMessageHandler captureReviewed(Fixture fixture) throws Exception {
        fixture.consumer.reviewed(fixture.message, fixture.channel, 7L);
        return capturedHandler(fixture, "moderation-blog-reviewed-v1", AsyncEventTypes.BLOG_REVIEWED);
    }

    private ReliableMessageHandler capturedHandler(Fixture fixture, String consumerName, String eventType)
            throws Exception {
        ArgumentCaptor<ReliableMessageHandler> handler = ArgumentCaptor.forClass(ReliableMessageHandler.class);
        verify(fixture.processor)
                .process(
                        eq(consumerName),
                        eq(eventType),
                        eq(fixture.message),
                        eq(fixture.channel),
                        eq(7L),
                        handler.capture());
        return handler.getValue();
    }

    private Fixture fixture() {
        ReliableMessageProcessor processor = mock(ReliableMessageProcessor.class);
        OutboundMailSender mail = mock(OutboundMailSender.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new Fixture(
                processor,
                mail,
                objectMapper,
                new ModerationMessageConsumer(processor, objectMapper, mail),
                mock(Channel.class),
                new Message(new byte[0]));
    }

    private record Fixture(
            ReliableMessageProcessor processor,
            OutboundMailSender mail,
            ObjectMapper objectMapper,
            ModerationMessageConsumer consumer,
            Channel channel,
            Message message) {
        MessageEnvelope envelope(String eventType) {
            return new MessageEnvelope(
                    "event-1",
                    eventType,
                    1,
                    0,
                    Instant.parse("2026-08-28T00:00:00Z"),
                    null,
                    "test-v1",
                    new byte[12],
                    new byte[] {1});
        }
    }
}
