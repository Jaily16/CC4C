package com.cc4c.shared;

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

@Configuration(proxyBeanMethods = false)
class ObservabilityMetricsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ObservabilityMetricsConfiguration.class);

    @Bean
    MeterFilter httpUriCardinalityFilter(ObservabilityProperties properties) {
        Set<String> acceptedUris = ConcurrentHashMap.newKeySet();
        AtomicBoolean logged = new AtomicBoolean();
        return new MeterFilter() {
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
