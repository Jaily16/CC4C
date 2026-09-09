package com.cc4c.support.messaging;

import com.cc4c.config.MessagingProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 根据消息命名空间生成 v1 交换机、主队列及重试队列名称，不直接声明 broker 资源。 */
@Component("messagingTopology")
public final class MessagingTopology {
    private final MessagingProperties properties;

    /**
     * 保存命名空间与重试延迟配置。
     *
     * @param properties 消息命名空间、密钥及确认重试配置
     */
    public MessagingTopology(MessagingProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成事件 topic 交换机名称。
     *
     * @return 命名空间.v1.events.x
     */
    public String eventExchange() {
        return prefix() + ".events.x";
    }

    /**
     * 生成死信 topic 交换机名称。
     *
     * @return 命名空间.v1.dead.x
     */
    public String deadExchange() {
        return prefix() + ".dead.x";
    }

    /**
     * 生成共享死信队列名称。
     *
     * @return 命名空间.v1.dead.q
     */
    public String deadQueue() {
        return prefix() + ".dead.q";
    }

    /**
     * 生成验证码邮件主队列名称。
     *
     * @return 命名空间下的验证码队列
     */
    public String verificationQueue() {
        return prefix() + ".identity.verification.q";
    }

    /**
     * 生成博客待审核通知主队列名称。
     *
     * @return 命名空间下的博客提交队列
     */
    public String blogSubmittedQueue() {
        return prefix() + ".moderation.blog-submitted.q";
    }

    /**
     * 生成博客审核结果通知主队列名称。
     *
     * @return 命名空间下的博客审核队列
     */
    public String blogReviewedQueue() {
        return prefix() + ".moderation.blog-reviewed.q";
    }

    /**
     * 按三个允许事件类型选择主队列前缀，生成带 30s、5m、30m 名称的三档重试队列。
     *
     * @param eventType 三个 v1 事件类型之一
     * @return 按重试档位排列的三个队列名
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
     * 读取配置中的三档实际重试延迟；队列名称本身不决定 TTL。
     *
     * @return 三档重试延迟
     */
    public List<Duration> retryDelays() {
        return properties.consumerRetryDelays();
    }

    /**
     * 按零起始档位选择指定事件的重试队列。
     *
     * @param eventType 三个 v1 事件类型之一
     * @param attemptIndex 从 0 起算的重试档位
     * @return 该档位的重试队列名
     */
    public String retryQueue(String eventType, int attemptIndex) {
        return retryQueues(eventType).get(attemptIndex);
    }

    /**
     * 建立三个 v1 事件类型到各自主队列的映射。
     *
     * @return 事件类型与主队列名称的只读映射
     */
    public Map<String, String> mainQueuesByEventType() {
        return Map.of(
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED, verificationQueue(),
                AsyncEventTypes.BLOG_SUBMITTED, blogSubmittedQueue(),
                AsyncEventTypes.BLOG_REVIEWED, blogReviewedQueue());
    }

    /**
     * 组合配置命名空间和 v1 拓扑版本。
     *
     * @return 消息资源名称前缀
     */
    private String prefix() {
        return properties.namespace() + ".v1";
    }
}
