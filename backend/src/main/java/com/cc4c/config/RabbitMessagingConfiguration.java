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

/**
 * 装配共享基础设施运行组件，并集中声明安全或基础设施策略。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class RabbitMessagingConfiguration {

    /**
     * 创建并配置 RabbitMessagingConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param topology 调用方提供的 {@code topology} 值
     * @return 当前操作产生的 Declarables 结果
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
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param extraArguments 调用方提供的 {@code extraArguments} 值
     * @return 当前操作产生的 Queue 结果
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
