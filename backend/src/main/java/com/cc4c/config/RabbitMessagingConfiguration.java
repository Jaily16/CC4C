package com.cc4c.config;

import com.cc4c.support.messaging.MessagingTopology;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用消息调度并声明隔离的事件、重试及死信拓扑。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class RabbitMessagingConfiguration {

    /**
     * 声明持久化交换机、主队列和三档 TTL 重试队列；死信队列保留 30 天。
     *
     * @param topology 基于消息命名空间生成的拓扑名称
     * @return 交由 RabbitAdmin 声明的交换机、队列和绑定集合
     */
    @Bean
    Declarables messagingDeclarables(MessagingTopology topology) {
        TopicExchange events = new TopicExchange(topology.eventExchange(), true, false);
        TopicExchange dead = new TopicExchange(topology.deadExchange(), true, false);
        List<Declarable> declarations = new ArrayList<>(List.of(events, dead));

        topology.mainQueuesByEventType().forEach((eventType, queueName) -> {
            Queue main = quorumQueue(
                    queueName,
                    Map.of(
                            "x-dead-letter-exchange",
                            topology.deadExchange(),
                            "x-dead-letter-routing-key",
                            eventType + ".dead"));
            declarations.add(main);
            declarations.add(BindingBuilder.bind(main).to(events).with(eventType));

            List<String> retryQueues = topology.retryQueues(eventType);
            List<Duration> retryDelays = topology.retryDelays();
            for (int index = 0; index < retryQueues.size(); index++) {
                Queue retry = quorumQueue(
                        retryQueues.get(index),
                        Map.of(
                                "x-message-ttl", retryDelays.get(index).toMillis(),
                                "x-dead-letter-exchange", topology.eventExchange(),
                                "x-dead-letter-routing-key", eventType));
                declarations.add(retry);
            }
        });

        Queue deadQueue = quorumQueue(
                topology.deadQueue(),
                Map.of("x-message-ttl", Duration.ofDays(30).toMillis()));
        Binding deadBinding = BindingBuilder.bind(deadQueue).to(dead).with("#");
        declarations.add(deadQueue);
        declarations.add(deadBinding);
        return new Declarables(declarations);
    }

    /**
     * 创建持久化 quorum 队列，限制 10 万条及 256 MiB；存在死信交换机时使用至少一次转移。
     *
     * @param name 队列名称
     * @param extraArguments TTL 或死信路由等附加队列参数
     * @return 达到容量上限时拒绝发布的队列定义
     */
    private Queue quorumQueue(String name, Map<String, Object> extraArguments) {
        Map<String, Object> arguments = new HashMap<>(extraArguments);
        arguments.put("x-queue-type", "quorum");
        arguments.put("x-overflow", "reject-publish");
        if (arguments.containsKey("x-dead-letter-exchange")) {
            arguments.put("x-dead-letter-strategy", "at-least-once");
        }
        arguments.put("x-max-length", 100_000L);
        arguments.put("x-max-length-bytes", 256L * 1024 * 1024);
        return new Queue(name, true, false, false, arguments);
    }
}
