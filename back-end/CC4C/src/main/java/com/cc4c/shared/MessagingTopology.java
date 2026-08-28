package com.cc4c.shared;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component("messagingTopology")
public final class MessagingTopology {
    private final MessagingProperties properties;

    public MessagingTopology(MessagingProperties properties) {
        this.properties = properties;
    }

    public String eventExchange() {
        return prefix() + ".events.x";
    }

    public String deadExchange() {
        return prefix() + ".dead.x";
    }

    public String deadQueue() {
        return prefix() + ".dead.q";
    }

    public String verificationQueue() {
        return prefix() + ".identity.verification.q";
    }

    public String blogSubmittedQueue() {
        return prefix() + ".moderation.blog-submitted.q";
    }

    public String blogReviewedQueue() {
        return prefix() + ".moderation.blog-reviewed.q";
    }

    public List<String> retryQueues(String eventType) {
        String base = switch (eventType) {
            case AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED -> verificationQueue();
            case AsyncEventTypes.BLOG_SUBMITTED -> blogSubmittedQueue();
            case AsyncEventTypes.BLOG_REVIEWED -> blogReviewedQueue();
            default -> throw new IllegalArgumentException("Unsupported async event type");
        };
        String retryBase = base.substring(0, base.length() - ".q".length());
        return List.of(
                retryBase + ".retry.30s.q",
                retryBase + ".retry.5m.q",
                retryBase + ".retry.30m.q");
    }

    public List<Duration> retryDelays() {
        return properties.consumerRetryDelays();
    }

    public String retryQueue(String eventType, int attemptIndex) {
        return retryQueues(eventType).get(attemptIndex);
    }

    public Map<String, String> mainQueuesByEventType() {
        return Map.of(
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED, verificationQueue(),
                AsyncEventTypes.BLOG_SUBMITTED, blogSubmittedQueue(),
                AsyncEventTypes.BLOG_REVIEWED, blogReviewedQueue());
    }

    private String prefix() {
        return properties.namespace() + ".v1";
    }
}
