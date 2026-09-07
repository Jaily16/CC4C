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

/**
 * PrometheusClient 负责独立观测门户的一项明确运行职责，并保持现有外部行为不变。
 */
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

    /**
     * 创建 PrometheusClient 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param properties 由容器注入的 PrometheusProperties 协作组件
     */
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

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param <T> 方法使用的类型参数
     * @param action 调用方提供的 {@code action} 值
     * @return 当前操作产生的 CompletableFuture<T> 结果
     */
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

    /**
     * 读取观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param definition 调用方提供的 {@code definition} 值
     * @param start 调用方提供的 {@code start} 值
     * @param end 调用方提供的 {@code end} 值
     * @param stepSeconds 调用方提供的 {@code stepSeconds} 值
     * @return 当前操作产生的 QueryResult 结果
     */
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

    /**
     * 读取观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param definition 调用方提供的 {@code definition} 值
     * @return 当前操作产生的 QueryResult 结果
     */
    QueryResult queryInstant(QueryDefinition definition) {
        JsonNode root = getJson("/api/v1/query", Map.of("query", definition.expression()));
        return parseResult(root, definition, false);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param allowedNames 调用方提供的 {@code allowedNames} 值
     * @return 当前操作产生的 Map<String,AlertState> 结果
     */
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

    /**
     * 读取观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    boolean ready() {
        try {
            byte[] body = send("/-/ready", Map.of());
            return body.length > 0;
        } catch (PrometheusSourceException exception) {
            return false;
        }
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     */
    @PreDestroy
    void close() {
        queries.shutdownNow();
    }

    /**
     * 解析观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param root 调用方提供的 {@code root} 值
     * @param definition 调用方提供的 {@code definition} 值
     * @param matrix 调用方提供的 {@code matrix} 值
     * @return 当前操作产生的 QueryResult 结果
     */
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

    /**
     * 解析观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param pair 调用方提供的 {@code pair} 值
     * @return 当前操作产生的 ParsedPoint 结果
     */
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

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param metric 调用方提供的 {@code metric} 值
     * @param allowedNames 调用方提供的 {@code allowedNames} 值
     * @return 当前操作产生的 Map<String,String> 结果
     */
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

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param template 调用方提供的 {@code template} 值
     * @param labels 调用方提供的 {@code labels} 值
     * @return 按当前协议生成或读取的字符串值
     */
    private String seriesName(String template, Map<String, String> labels) {
        String value = template;
        for (Map.Entry<String, String> label : labels.entrySet()) {
            value = value.replace("{{" + label.getKey() + "}}", label.getValue());
        }
        return truncate(value.replaceAll("\\{\\{[^}]+}}", "未知"), 160);
    }

    /**
     * 读取观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param path 已验证边界内的文件或请求路径
     * @param parameters 调用方提供的 {@code parameters} 值
     * @return 当前操作产生的 JsonNode 结果
     */
    private JsonNode getJson(String path, Map<String, String> parameters) {
        byte[] body = send(path, parameters);
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new PrometheusSourceException();
        }
    }

    /**
     * 发布观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param path 已验证边界内的文件或请求路径
     * @param parameters 调用方提供的 {@code parameters} 值
     * @return 按当前方法约定返回结果集合
     */
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

    /**
     * 校验观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param root 调用方提供的 {@code root} 值
     */
    private void requireSuccess(JsonNode root) {
        if (!"success".equals(root.path("status").asText())) {
            throw new PrometheusSourceException();
        }
    }

    /**
     * 编码或保护观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param value 待处理或存储的值
     * @return 按当前协议生成或读取的字符串值
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param value 待处理或存储的值
     * @param maximum 调用方提供的 {@code maximum} 值
     * @return 按当前协议生成或读取的字符串值
     */
    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param value 待处理或存储的值
     * @param allowed 调用方提供的 {@code allowed} 值
     * @param fallback 调用方提供的 {@code fallback} 值
     * @return 按当前协议生成或读取的字符串值
     */
    private static String allowed(String value, Set<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    /**
     * 解析观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param value 待处理或存储的值
     * @return 当前操作产生的 Instant 结果
     */
    private static Instant parseInstant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * 以不可变结构承载独立观测门户计算或查询结果。
     *
     * @param series 调用方提供的 {@code series} 值
     * @param partial 调用方提供的 {@code partial} 值
     */
    record QueryResult(List<SeriesResponse> series, boolean partial) {}

    /**
     * AlertState 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param state 调用方提供的 {@code state} 值
     * @param health 调用方提供的 {@code health} 值
     * @param lastEvaluationAt 当前操作使用的时间点
     */
    record AlertState(String state, String health, Instant lastEvaluationAt) {}

    /**
     * 表示独立观测门户处理中可分类且可安全映射的失败。
     */
    static final class PrometheusSourceException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /**
     * ParsedPoint 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param point 调用方提供的 {@code point} 值
     * @param partial 调用方提供的 {@code partial} 值
     */
    private record ParsedPoint(PointResponse point, boolean partial) {}

    /**
     * RawSeries 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param labels 调用方提供的 {@code labels} 值
     * @param points 调用方提供的 {@code points} 值
     */
    private record RawSeries(String name, Map<String, String> labels, List<PointResponse> points) {
        /**
         * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @return 按当前协议生成或读取的字符串值
         */
        String sortKey() {
            return name + labels;
        }
    }
}
