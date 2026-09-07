package com.cc4c.observability;

import com.cc4c.observability.ObservabilityCatalog.QueryDefinition;
import com.cc4c.observability.ObservabilityDtos.PointResponse;
import com.cc4c.observability.ObservabilityDtos.SeriesResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** PrometheusClient 执行有界固定查询，并在进入 HTTP 响应前剥离未授权标签和内部故障。 */
@Component
public final class PrometheusClient {
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SERIES = 50;
    private static final int MAX_POINTS = 300;

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String authorization;
    private final ThreadPoolExecutor queries;
    private final HttpClient httpClient;

    PrometheusClient(ObjectMapper objectMapper, PrometheusProperties properties) {
        this.objectMapper = objectMapper;
        this.baseUrl = properties.url().toString().replaceAll("/+$", "");
        this.authorization = properties.authenticated()
                ? "Basic "
                        + Base64.getEncoder()
                                .encodeToString((properties.username() + ":" + properties.password())
                                        .getBytes(StandardCharsets.UTF_8))
                : null;
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "cc4c-prometheus-query-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.queries = new ThreadPoolExecutor(
                4,
                4,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    <T> CompletableFuture<T> submit(Callable<T> action) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return action.call();
                        } catch (RuntimeException exception) {
                            throw exception;
                        } catch (Exception exception) {
                            throw new CompletionException(new PrometheusSourceException());
                        }
                    },
                    queries);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new PrometheusSourceException());
        }
    }

    QueryResult queryRange(QueryDefinition definition, Instant start, Instant end, long stepSeconds) {
        JsonNode root = getJson(
                "/api/v1/query_range",
                Map.of(
                        "query", definition.expression(),
                        "start", Long.toString(start.getEpochSecond()),
                        "end", Long.toString(end.getEpochSecond()),
                        "step", Long.toString(stepSeconds)));
        return parseResult(root, definition, true);
    }

    QueryResult queryInstant(QueryDefinition definition) {
        JsonNode root = getJson("/api/v1/query", Map.of("query", definition.expression()));
        return parseResult(root, definition, false);
    }

    Map<String, AlertState> alertRules(Set<String> allowedNames) {
        JsonNode root = getJson("/api/v1/rules", Map.of("type", "alert"));
        requireSuccess(root);
        Map<String, AlertState> rules = new LinkedHashMap<>();
        for (JsonNode group : root.path("data").path("groups")) {
            for (JsonNode rule : group.path("rules")) {
                String name = rule.path("name").asText("");
                if (!allowedNames.contains(name) || rules.containsKey(name)) {
                    continue;
                }
                String state = allowed(
                        rule.path("state").asText("inactive"), Set.of("firing", "pending", "inactive"), "inactive");
                String health =
                        allowed(rule.path("health").asText("unknown"), Set.of("ok", "err", "unknown"), "unknown");
                rules.put(
                        name,
                        new AlertState(
                                state,
                                health,
                                parseInstant(rule.path("lastEvaluation").asText(null))));
            }
        }
        return rules;
    }

    boolean ready() {
        try {
            byte[] body = send("/-/ready", Map.of());
            return body.length > 0;
        } catch (PrometheusSourceException exception) {
            return false;
        }
    }

    @PreDestroy
    void close() {
        queries.shutdownNow();
    }

    private QueryResult parseResult(JsonNode root, QueryDefinition definition, boolean matrix) {
        requireSuccess(root);
        String expectedType = matrix ? "matrix" : "vector";
        JsonNode data = root.path("data");
        if (!expectedType.equals(data.path("resultType").asText())) {
            throw new PrometheusSourceException();
        }
        List<RawSeries> raw = new ArrayList<>();
        boolean partial = false;
        int inputSeries = 0;
        for (JsonNode result : data.path("result")) {
            inputSeries++;
            if (raw.size() >= MAX_SERIES) {
                partial = true;
                continue;
            }
            Map<String, String> labels = allowedLabels(result.path("metric"), definition.allowedLabels());
            List<PointResponse> points = new ArrayList<>();
            JsonNode values = matrix ? result.path("values") : null;
            if (matrix) {
                int inputPoints = 0;
                for (JsonNode value : values) {
                    inputPoints++;
                    if (points.size() >= MAX_POINTS) {
                        partial = true;
                        continue;
                    }
                    ParsedPoint point = parsePoint(value);
                    points.add(point.point());
                    partial |= point.partial();
                }
            } else {
                ParsedPoint point = parsePoint(result.path("value"));
                points.add(point.point());
                partial |= point.partial();
            }
            if (!points.isEmpty()) {
                raw.add(new RawSeries(seriesName(definition.legend(), labels), labels, points));
            }
        }
        raw.sort(Comparator.comparing(RawSeries::sortKey));
        List<SeriesResponse> series = raw.stream()
                .map(item -> new SeriesResponse(item.name(), item.labels(), List.copyOf(item.points())))
                .toList();
        return new QueryResult(series, partial || inputSeries > MAX_SERIES);
    }

    private ParsedPoint parsePoint(JsonNode pair) {
        if (!pair.isArray() || pair.size() != 2 || !pair.get(0).isNumber()) {
            throw new PrometheusSourceException();
        }
        long timestamp = Math.round(pair.get(0).asDouble() * 1000.0);
        String encoded = pair.get(1).asText();
        try {
            double value = Double.parseDouble(encoded);
            if (Double.isFinite(value)) {
                return new ParsedPoint(new PointResponse(timestamp, value), false);
            }
            return new ParsedPoint(new PointResponse(timestamp, null), true);
        } catch (NumberFormatException exception) {
            return new ParsedPoint(new PointResponse(timestamp, null), true);
        }
    }

    private Map<String, String> allowedLabels(JsonNode metric, List<String> allowedNames) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String name : allowedNames) {
            JsonNode value = metric.get(name);
            if (value != null && value.isTextual()) {
                labels.put(name, truncate(value.asText(), 120));
            }
        }
        return Map.copyOf(labels);
    }

    private String seriesName(String template, Map<String, String> labels) {
        String value = template;
        for (Map.Entry<String, String> label : labels.entrySet()) {
            value = value.replace("{{" + label.getKey() + "}}", label.getValue());
        }
        return truncate(value.replaceAll("\\{\\{[^}]+}}", "未知"), 160);
    }

    private JsonNode getJson(String path, Map<String, String> parameters) {
        byte[] body = send(path, parameters);
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new PrometheusSourceException();
        }
    }

    private byte[] send(String path, Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        URI uri = URI.create(baseUrl + path + (query.isEmpty() ? "" : "?" + query));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header(HttpHeaders.ACCEPT, "application/json")
                .GET();
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                byte[] body = input.readNBytes(MAX_BODY_BYTES + 1);
                if (response.statusCode() != 200 || body.length > MAX_BODY_BYTES) {
                    throw new PrometheusSourceException();
                }
                return body;
            }
        } catch (IOException exception) {
            throw new PrometheusSourceException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PrometheusSourceException();
        }
    }

    private void requireSuccess(JsonNode root) {
        if (!"success".equals(root.path("status").asText())) {
            throw new PrometheusSourceException();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String allowed(String value, Set<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    record QueryResult(List<SeriesResponse> series, boolean partial) {}

    record AlertState(String state, String health, Instant lastEvaluationAt) {}

    static final class PrometheusSourceException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record ParsedPoint(PointResponse point, boolean partial) {}

    private record RawSeries(String name, Map<String, String> labels, List<PointResponse> points) {
        String sortKey() {
            return name + labels;
        }
    }
}
