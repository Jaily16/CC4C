package com.cc4c.support.monitoring;

import com.cc4c.config.PrometheusProperties;
import com.cc4c.dto.ObservabilityDtos.PointResponse;
import com.cc4c.dto.ObservabilityDtos.SeriesResponse;
import com.cc4c.support.monitoring.ObservabilityCatalog.QueryDefinition;
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

/** 使用有界线程池向固定 Prometheus 地址发送只读请求，限制响应体、序列、点数和可公开标签。 */
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
     * 构造四线程、三十二项等待队列和禁止重定向的 HTTP 客户端，保存可选 Basic 认证头。
     *
     * @param objectMapper 解析受控 JSON 的映射器
     * @param properties 该组件使用的观测或 Prometheus 设置
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
     * 将查询任务提交到有界线程池；队列满或受检异常以脱敏来源异常完成 Future。
     *
     * @param <T> 被观测对象或查询结果的类型
     * @param action 在查询线程池中执行的任务
     * @return 异步查询结果；提交拒绝时为失败的 Future
     */
    public <T> CompletableFuture<T> submit(Callable<T> action) {
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
     * 按目录中的固定表达式请求时间范围查询，并解析有界时序数据。
     *
     * @param definition 包含固定表达式和公开标签规则的查询定义
     * @param start 范围查询开始时间
     * @param end 范围查询结束时间
     * @param stepSeconds 相邻查询采样点的间隔秒数
     * @return 包含序列和部分结果标记的查询结果
     */
    public QueryResult queryRange(QueryDefinition definition, Instant start, Instant end, long stepSeconds) {
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
     * 按目录中的固定表达式请求即时查询。
     *
     * @param definition 包含固定表达式和公开标签规则的查询定义
     * @return 包含即时采样序列和部分结果标记的查询结果
     */
    public QueryResult queryInstant(QueryDefinition definition) {
        JsonNode root = getJson("/api/v1/query", Map.of("query", definition.expression()));
        return parseResult(root, definition, false);
    }

    /**
     * 读取告警规则，只保留批准名称的首条规则，并归一化状态及健康字段。
     *
     * @param allowedNames 允许返回的告警或标签名称
     * @return 以允许的告警名称为键的规则状态
     */
    public Map<String, AlertState> alertRules(Set<String> allowedNames) {
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
     * 请求就绪端点；仅当 HTTP 请求成功且响应非空时视为就绪。
     *
     * @return 就绪请求返回非空正文时为 true，来源异常时为 false
     */
    public boolean ready() {
        try {
            byte[] body = send("/-/ready", Map.of());
            return body.length > 0;
        } catch (PrometheusSourceException exception) {
            return false;
        }
    }

    /** 关闭查询线程池并中断尚未结束的任务。 */
    @PreDestroy
    void close() {
        queries.shutdownNow();
    }

    /**
     * 校验 vector 或 matrix 响应，最多保留 50 个序列、每序列 300 点；截断或非有限值标记为部分结果。
     *
     * @param root Prometheus JSON 响应根节点
     * @param definition 包含固定表达式和公开标签规则的查询定义
     * @param matrix 是否要求时间范围 matrix 响应
     * @return 排序后的公开序列及部分结果标记
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
     * 解析时间戳与数值对，将秒换为毫秒；非有限或无法解析的数值置空并标记部分结果。
     *
     * @param pair 包含秒时间戳和数值的二元 JSON 数组
     * @return 带毫秒时间戳的数据点及其部分结果标记
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
     * 仅复制目录批准的文本标签，并将每个值截到 120 个 UTF-16 代码单元。
     *
     * @param metric 原始序列标签节点
     * @param allowedNames 允许返回的告警或标签名称
     * @return 不可变的公开标签映射
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
     * 把图例占位符替换为公开标签，未匹配的占位符显示为未知，并截到 160 个 UTF-16 代码单元。
     *
     * @param template 目录提供的图例模板
     * @param labels 已经筛选的公开标签
     * @return 用于门户展示的序列名
     */
    private String seriesName(String template, Map<String, String> labels) {
        String value = template;
        for (Map.Entry<String, String> label : labels.entrySet()) {
            value = value.replace("{{" + label.getKey() + "}}", label.getValue());
        }
        return truncate(value.replaceAll("\\{\\{[^}]+}}", "未知"), 160);
    }

    /**
     * 发送只读请求并解析 JSON；解析失败只抛出脱敏来源异常。
     *
     * @param path 固定 Prometheus API 路径
     * @param parameters 待编码的查询参数
     * @return 响应的 JSON 根节点
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
     * 按名称排序并编码查询参数，发送超时 5 秒的 GET；只接受 200 状态及不超过 2 MiB 的响应。
     *
     * @param path 固定 Prometheus API 或就绪路径
     * @param parameters 待编码的查询参数
     * @return 受限大小的原始响应字节
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
     * 要求 Prometheus JSON 的 status 为 success，否则抛出来源异常。
     *
     * @param root Prometheus JSON 响应根节点
     */
    private void requireSuccess(JsonNode root) {
        if (!"success".equals(root.path("status").asText())) {
            throw new PrometheusSourceException();
        }
    }

    /**
     * 使用 UTF-8 编码 URL 参数，并把空格的加号表示替换为百分号编码。
     *
     * @param value 需要 URL 编码的参数文本
     * @return 可拼接到查询字符串中的编码值
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 按 UTF-16 代码单元保留字符串前缀。
     *
     * @param value 需要限制长度的文本
     * @param maximum 允许的最大 UTF-16 代码单元数
     * @return 长度不超过上限的字符串
     */
    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    /**
     * 保留白名单中的字段值，否则返回指定兜底值。
     *
     * @param value 需要校验的上游字段值
     * @param allowed 字段允许值集合
     * @param fallback 不在白名单中时使用的值
     * @return 批准的值或兜底值
     */
    private static String allowed(String value, Set<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    /**
     * 解析 ISO-8601 时间；空值或格式错误返回 null。
     *
     * @param value ISO-8601 时间文本，可为 null
     * @return 解析出的时间点，无法解析时为 null
     */
    private static Instant parseInstant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * 承载公开时序数据和截断或缺失值的标记。
     *
     * @param series 已限制大小的公开序列列表
     * @param partial 是否存在截断或无法表示的数值
     */
    public record QueryResult(List<SeriesResponse> series, boolean partial) {}

    /**
     * 承载告警状态、规则健康与最近评估时间。
     *
     * @param state firing、pending 或 inactive 状态
     * @param health ok、err 或 unknown 规则健康状态
     * @param lastEvaluationAt 最后评估时间，无法解析时为 null
     */
    public record AlertState(String state, String health, Instant lastEvaluationAt) {}

    /** 表示 Prometheus 来源失败，不携带上游正文、认证信息或底层异常消息。 */
    static final class PrometheusSourceException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 承载单点解析结果及数值缺失标记。
     *
     * @param point 带毫秒时间戳的数据点
     * @param partial 是否存在截断或无法表示的数值
     */
    private record ParsedPoint(PointResponse point, boolean partial) {}

    /**
     * 暂存公开标签、图例名称和点列表，供最终结果排序转换。
     *
     * @param name 序列展示名称
     * @param labels 已经筛选的公开标签
     * @param points 该序列的数据点列表
     */
    private record RawSeries(String name, Map<String, String> labels, List<PointResponse> points) {
        /**
         * 拼接序列名与标签表示作为排序键。
         *
         * @return 当前序列的排序字符串
         */
        String sortKey() {
            return name + labels;
        }
    }
}
