package com.cc4c.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配共享基础设施运行组件，并集中声明安全或基础设施策略。
 */
@Configuration(proxyBeanMethods = false)
class ObservabilityMetricsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ObservabilityMetricsConfiguration.class);

    /**
     * 创建并配置 ObservabilityMetricsConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param properties 由容器注入的 ObservabilityProperties 协作组件
     * @return 当前操作产生的 MeterFilter 结果
     */
    @Bean
    MeterFilter httpUriCardinalityFilter(ObservabilityProperties properties) {
        Set<String> acceptedUris = ConcurrentHashMap.newKeySet();
        AtomicBoolean logged = new AtomicBoolean();
        return new MeterFilter() {
            /**
             * 处理观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
             *
             * @param id 调用方提供的 {@code id} 值
             * @return 当前操作产生的 MeterFilterReply 结果
             */
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if (!"http.server.requests".equals(id.getName())) {
                    return MeterFilterReply.NEUTRAL;
                }
                String uri = id.getTag("uri");
                if (uri == null || acceptedUris.contains(uri)) {
                    return MeterFilterReply.NEUTRAL;
                }
                if (acceptedUris.size() < properties.maxHttpUriTags()) {
                    acceptedUris.add(uri);
                    return MeterFilterReply.NEUTRAL;
                }
                if (logged.compareAndSet(false, true)) {
                    logger.atWarn()
                            .addKeyValue("event", "http_metric_uri_cardinality_limited")
                            .addKeyValue("limit", properties.maxHttpUriTags())
                            .log("HTTP metric URI cardinality limit reached");
                }
                return MeterFilterReply.DENY;
            }
        };
    }
}
