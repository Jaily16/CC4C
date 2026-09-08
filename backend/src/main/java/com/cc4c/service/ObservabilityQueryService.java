package com.cc4c.service;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.dto.ObservabilityDtos.AlertRuleResponse;
import com.cc4c.dto.ObservabilityDtos.AlertsResponse;
import com.cc4c.dto.ObservabilityDtos.DashboardResponse;
import com.cc4c.dto.ObservabilityDtos.MetricCardResponse;
import com.cc4c.dto.ObservabilityDtos.OverviewResponse;
import com.cc4c.dto.ObservabilityDtos.PanelResponse;
import com.cc4c.dto.ObservabilityDtos.PanelStatus;
import com.cc4c.dto.ObservabilityDtos.SeriesResponse;
import com.cc4c.dto.ObservabilityDtos.SourceStatus;
import com.cc4c.support.monitoring.ObservabilityCatalog;
import com.cc4c.support.monitoring.ObservabilityCatalog.AlertDefinition;
import com.cc4c.support.monitoring.ObservabilityCatalog.DashboardDefinition;
import com.cc4c.support.monitoring.ObservabilityCatalog.PanelDefinition;
import com.cc4c.support.monitoring.ObservabilityCatalog.QueryDefinition;
import com.cc4c.support.monitoring.PrometheusClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 协调独立观测门户用例及其持久化、安全和外部协作边界。
 */
@Service
public final class ObservabilityQueryService {
    private final ObservabilityCatalog catalog;
    private final PrometheusClient prometheus;

    /**
     * 创建 ObservabilityQueryService 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param catalog 调用方提供的 {@code catalog} 值
     * @param prometheus 由容器注入的 PrometheusClient 协作组件
     */
    ObservabilityQueryService(ObservabilityCatalog catalog, PrometheusClient prometheus) {
        this.catalog = catalog;
        this.prometheus = prometheus;
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 当前操作产生的 OverviewResponse 结果
     */
    public OverviewResponse overview() {
        Instant generatedAt = Instant.now();
        List<CompletableFuture<PrometheusClient.QueryResult>> futures = catalog.overview().stream()
                .map(query -> prometheus.submit(() -> prometheus.queryInstant(query)))
                .toList();
        List<MetricCardResponse> metrics = new ArrayList<>();
        int errors = 0;
        int available = 0;
        boolean partial = false;
        for (int index = 0; index < catalog.overview().size(); index++) {
            QueryDefinition definition = catalog.overview().get(index);
            try {
                PrometheusClient.QueryResult result = futures.get(index).join();
                Double value = lastValue(result.series());
                if (value == null) {
                    metrics.add(new MetricCardResponse(
                            definition.id(), definition.title(), definition.unit(), PanelStatus.EMPTY, null, "暂无数据"));
                } else {
                    available++;
                    partial |= result.partial();
                    metrics.add(new MetricCardResponse(
                            definition.id(), definition.title(), definition.unit(), PanelStatus.OK, value, null));
                }
            } catch (CompletionException exception) {
                errors++;
                metrics.add(new MetricCardResponse(
                        definition.id(),
                        definition.title(),
                        definition.unit(),
                        PanelStatus.ERROR,
                        null,
                        "Prometheus 暂时不可用"));
            }
        }
        return new OverviewResponse(generatedAt, sourceStatus(errors, available, partial), List.copyOf(metrics));
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param dashboardId 目标对象的稳定标识
     * @param requestedRange 调用方提供的 {@code requestedRange} 值
     * @return 当前操作产生的 DashboardResponse 结果
     */
    public DashboardResponse dashboard(String dashboardId, String requestedRange) {
        DashboardDefinition dashboard = catalog.dashboard(dashboardId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "观测面板不存在"));
        Range range = Range.parse(requestedRange);
        Instant generatedAt = Instant.now();
        Instant start = generatedAt.minus(range.duration());
        List<PendingPanel> pending = dashboard.panels().stream()
                .map(panel -> new PendingPanel(
                        panel,
                        panel.queries().stream()
                                .map(query -> prometheus.submit(
                                        () -> prometheus.queryRange(query, start, generatedAt, range.stepSeconds())))
                                .toList()))
                .toList();

        List<PanelResponse> panels = new ArrayList<>();
        int errors = 0;
        int available = 0;
        boolean partial = false;
        for (PendingPanel item : pending) {
            PanelAggregate aggregate = aggregate(item);
            panels.add(aggregate.response());
            errors += aggregate.errors();
            available += aggregate.available();
            partial |= aggregate.partial();
        }
        return new DashboardResponse(
                dashboard.id(),
                dashboard.title(),
                range.id(),
                range.stepSeconds(),
                generatedAt,
                sourceStatus(errors, available, partial),
                List.copyOf(panels));
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 当前操作产生的 AlertsResponse 结果
     */
    public AlertsResponse alerts() {
        Instant generatedAt = Instant.now();
        List<AlertDefinition> definitions = catalog.alerts();
        Set<String> names = definitions.stream().map(AlertDefinition::id).collect(java.util.stream.Collectors.toSet());
        Map<String, PrometheusClient.AlertState> states;
        try {
            states = prometheus.submit(() -> prometheus.alertRules(names)).join();
        } catch (CompletionException exception) {
            List<AlertRuleResponse> unavailable = definitions.stream()
                    .map(definition -> alertResponse(definition, null, "Prometheus 暂时不可用"))
                    .toList();
            return new AlertsResponse(generatedAt, SourceStatus.UNAVAILABLE, definitions.size(), 0, 0, 0, unavailable);
        }

        int firing = 0;
        int pending = 0;
        List<AlertRuleResponse> rules = new ArrayList<>();
        for (AlertDefinition definition : definitions) {
            PrometheusClient.AlertState state = states.get(definition.id());
            if (state != null && "firing".equals(state.state())) {
                firing++;
            }
            if (state != null && "pending".equals(state.state())) {
                pending++;
            }
            rules.add(alertResponse(definition, state, state == null ? "Prometheus 未返回该规则" : null));
        }
        SourceStatus status = states.isEmpty()
                ? SourceStatus.EMPTY
                : states.size() == definitions.size() ? SourceStatus.AVAILABLE : SourceStatus.PARTIAL;
        return new AlertsResponse(
                generatedAt, status, definitions.size(), states.size(), firing, pending, List.copyOf(rules));
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param pending 调用方提供的 {@code pending} 值
     * @return 当前操作产生的 PanelAggregate 结果
     */
    private PanelAggregate aggregate(PendingPanel pending) {
        List<SeriesResponse> series = new ArrayList<>();
        int errors = 0;
        boolean partial = false;
        for (CompletableFuture<PrometheusClient.QueryResult> future : pending.futures()) {
            try {
                PrometheusClient.QueryResult result = future.join();
                series.addAll(result.series());
                partial |= result.partial();
            } catch (CompletionException exception) {
                errors++;
            }
        }
        PanelStatus status;
        String message;
        if (series.isEmpty() && errors > 0) {
            status = PanelStatus.ERROR;
            message = "Prometheus 暂时不可用";
        } else if (series.isEmpty()) {
            status = PanelStatus.EMPTY;
            message = "当前范围暂无数据";
        } else {
            status = PanelStatus.OK;
            message = errors > 0 || partial ? "部分数据不可用或已截断" : null;
        }
        PanelResponse response = new PanelResponse(
                pending.definition().id(),
                pending.definition().title(),
                pending.definition().unit(),
                status,
                List.copyOf(series),
                partial,
                message);
        return new PanelAggregate(response, errors, series.isEmpty() ? 0 : 1, partial);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param definition 调用方提供的 {@code definition} 值
     * @param state 调用方提供的 {@code state} 值
     * @param message 当前处理的消息或用户提示
     * @return 当前操作产生的 AlertRuleResponse 结果
     */
    private AlertRuleResponse alertResponse(
            AlertDefinition definition, PrometheusClient.AlertState state, String message) {
        return new AlertRuleResponse(
                definition.id(),
                definition.title(),
                definition.description(),
                definition.severity(),
                state == null ? "MISSING" : state.state().toUpperCase(Locale.ROOT),
                state == null ? "UNKNOWN" : state.health().toUpperCase(Locale.ROOT),
                state == null ? null : state.lastEvaluationAt(),
                message);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param series 调用方提供的 {@code series} 值
     * @return 按当前规则计算或读取的数值
     */
    private Double lastValue(List<SeriesResponse> series) {
        if (series.isEmpty() || series.getFirst().points().isEmpty()) {
            return null;
        }
        return series.getFirst().points().getLast().value();
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param errors 调用方提供的 {@code errors} 值
     * @param available 调用方提供的 {@code available} 值
     * @param partial 调用方提供的 {@code partial} 值
     * @return 当前操作产生的 SourceStatus 结果
     */
    private SourceStatus sourceStatus(int errors, int available, boolean partial) {
        if (errors > 0 && available == 0) {
            return SourceStatus.UNAVAILABLE;
        }
        if (errors > 0 || partial) {
            return SourceStatus.PARTIAL;
        }
        return available == 0 ? SourceStatus.EMPTY : SourceStatus.AVAILABLE;
    }

    /**
     * PendingPanel 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param definition 调用方提供的 {@code definition} 值
     * @param futures 调用方提供的 {@code futures} 值
     */
    private record PendingPanel(
            PanelDefinition definition, List<CompletableFuture<PrometheusClient.QueryResult>> futures) {}

    /**
     * PanelAggregate 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @param errors 调用方提供的 {@code errors} 值
     * @param available 调用方提供的 {@code available} 值
     * @param partial 调用方提供的 {@code partial} 值
     */
    private record PanelAggregate(PanelResponse response, int errors, int available, boolean partial) {}

    /**
     * Range 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param duration 调用方提供的 {@code duration} 值
     * @param stepSeconds 调用方提供的 {@code stepSeconds} 值
     */
    private record Range(String id, Duration duration, long stepSeconds) {
        private static final Map<String, Range> ALLOWED = new LinkedHashMap<>();

        static {
            ALLOWED.put("15m", new Range("15m", Duration.ofMinutes(15), 15));
            ALLOWED.put("1h", new Range("1h", Duration.ofHours(1), 30));
            ALLOWED.put("6h", new Range("6h", Duration.ofHours(6), 120));
            ALLOWED.put("24h", new Range("24h", Duration.ofHours(24), 300));
        }

        /**
         * 解析当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @param candidate 调用方提供的 {@code candidate} 值
         * @return 当前操作产生的 Range 结果
         */
        static Range parse(String candidate) {
            Range range = ALLOWED.get(candidate);
            if (range == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, BusinessCode.VALIDATION_ERROR, "观测时间范围无效");
            }
            return range;
        }
    }
}
