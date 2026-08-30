package com.cc4c.shared;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** CorrelationIds 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class CorrelationIds {
    public static final String HEADER = "X-Request-ID";
    public static final String AMQP_HEADER = "X-CC4C-Correlation-Id";
    public static final String MDC_KEY = "request_id";
    public static final String REQUEST_ATTRIBUTE = CorrelationIds.class.getName() + ".requestId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    private CorrelationIds() {}

    public static String normalizeOrGenerate(String candidate) {
        return normalize(candidate, UUID.randomUUID().toString());
    }

    public static String normalize(String candidate, String fallback) {
        if (candidate != null && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        if (fallback != null && SAFE.matcher(fallback).matches()) {
            return fallback;
        }
        return UUID.randomUUID().toString();
    }

    public static String currentOr(String fallback) {
        return normalize(MDC.get(MDC_KEY), fallback);
    }

    public static Scope open(String correlationId) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, normalizeOrGenerate(correlationId));
        return new Scope(previous);
    }

    /** Scope 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
    public static final class Scope implements AutoCloseable {
        private final String previous;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
