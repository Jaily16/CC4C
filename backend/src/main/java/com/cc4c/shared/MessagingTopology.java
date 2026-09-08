package com.cc4c.shared;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MessagingTopology 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component("messagingTopology")
public final class MessagingTopology {
    private final MessagingProperties properties;

    /**
     * 创建 MessagingTopology 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param properties 调用方提供的 {@code properties} 值
     */
    public MessagingTopology(MessagingProperties properties) {
        this.properties = properties;
    }

    /**
     * 执行 MessagingTopology 中的 eventExchange 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String eventExchange() {
        return prefix() + ".events.x";
    }

    /**
     * 执行 MessagingTopology 中的 deadExchange 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String deadExchange() {
        return prefix() + ".dead.x";
    }

    /**
     * 执行 MessagingTopology 中的 deadQueue 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String deadQueue() {
        return prefix() + ".dead.q";
    }

    /**
     * 执行 MessagingTopology 中的 verificationQueue 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String verificationQueue() {
        return prefix() + ".identity.verification.q";
    }

    /**
     * 执行 MessagingTopology 中的 blogSubmittedQueue 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String blogSubmittedQueue() {
        return prefix() + ".moderation.blog-submitted.q";
    }

    /**
     * 执行 MessagingTopology 中的 blogReviewedQueue 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String blogReviewedQueue() {
        return prefix() + ".moderation.blog-reviewed.q";
    }

    /**
     * 执行 MessagingTopology 中的 retryQueues 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventType 调用方提供的 {@code eventType} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public List<String> retryQueues(String eventType) {
        String base =
                switch (eventType) {
                    case AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED -> verificationQueue();
                    case AsyncEventTypes.BLOG_SUBMITTED -> blogSubmittedQueue();
                    case AsyncEventTypes.BLOG_REVIEWED -> blogReviewedQueue();
                    default -> throw new IllegalArgumentException("Unsupported async event type");
                };
        String retryBase = base.substring(0, base.length() - ".q".length());
        return List.of(retryBase + ".retry.30s.q", retryBase + ".retry.5m.q", retryBase + ".retry.30m.q");
    }

    /**
     * 执行 MessagingTopology 中的 retryDelays 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public List<Duration> retryDelays() {
        return properties.consumerRetryDelays();
    }

    /**
     * 执行 MessagingTopology 中的 retryQueue 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param attemptIndex 调用方提供的 {@code attemptIndex} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String retryQueue(String eventType, int attemptIndex) {
        return retryQueues(eventType).get(attemptIndex);
    }

    /**
     * 执行 MessagingTopology 中的 mainQueuesByEventType 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public Map<String, String> mainQueuesByEventType() {
        return Map.of(
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED, verificationQueue(),
                AsyncEventTypes.BLOG_SUBMITTED, blogSubmittedQueue(),
                AsyncEventTypes.BLOG_REVIEWED, blogReviewedQueue());
    }

    /**
     * 执行 MessagingTopology 中的 prefix 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String prefix() {
        return properties.namespace() + ".v1";
    }
}
