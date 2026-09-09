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

/** 限制 HTTP 请求指标的 URI 标签基数，防止动态路径无限增加时间序列。 */
@Configuration(proxyBeanMethods = false)
class ObservabilityMetricsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ObservabilityMetricsConfiguration.class);

    /**
     * 创建进程内共享已接纳 URI 集合的过滤器，达到上限后只放行已接纳 URI。
     *
     * @param properties 对应组件的类型化配置
     * @return 只约束 http.server.requests 的指标过滤器
     */
    @Bean
    MeterFilter httpUriCardinalityFilter(ObservabilityProperties properties) {
        Set<String> acceptedUris = ConcurrentHashMap.newKeySet();
        AtomicBoolean logged = new AtomicBoolean();
        return new MeterFilter() {
            /**
             * 保留已有 URI 和其他指标；超过上限时拒绝新 URI，并仅记录一次告警。
             *
             * @param id 待注册指标的名称及标签
             * @return 放行返回 NEUTRAL，拒绝返回 DENY
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
