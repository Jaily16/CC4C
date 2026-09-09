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

/** 执行目录中预定义的观测查询，将并发结果聚合为总览、Dashboard 和告警展示状态。 */
@Service
public final class ObservabilityQueryService {
    private final ObservabilityCatalog catalog;
    private final PrometheusClient prometheus;

    /**
     * 接入只含固定查询的观测目录和有界 Prometheus 客户端。
     *
     * @param catalog 固定查询、面板及告警目录
     * @param prometheus 固定查询及就绪探测的 Prometheus 客户端
     */
    ObservabilityQueryService(ObservabilityCatalog catalog, PrometheusClient prometheus) {
        this.catalog = catalog;
        this.prometheus = prometheus;
    }

    /**
     * 并发执行固定总览即时查询，按目录顺序返回卡片；区分空值、有效值和查询失败。
     *
     * @return 带聚合数据源状态的总览
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
     * 校验固定 Dashboard 和时间范围后并发查询面板，按配置步长聚合曲线及部分失败标志。
     *
     * @param dashboardId 目录内 Dashboard 标识
     * @param requestedRange 15m、1h、6h 或 24h
     * @return 指定范围内的 Dashboard 结果
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
     * 仅查询目录内告警名称，统计加载、firing 和 pending 数量；失败或缺失规则使用明确状态。
     *
     * @return 告警规则及加载完整性汇总
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
     * 合并面板各查询的曲线；有数据时保留成功状态并提示部分失败，无数据时区分 EMPTY 和 ERROR。
     *
     * @param pending 待汇总的面板定义与查询任务
     * @return 面板响应及供上层汇总的计数
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
     * 合并规则定义和运行状态；缺失状态用 MISSING、UNKNOWN 及空评估时间表示。
     *
     * @param definition 预定义告警名称和展示字段
     * @param state 可空的 Prometheus 规则状态
     * @param message 查询失败或规则缺失的展示提示
     * @return 单条告警展示响应
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
     * 读取第一条序列的最后一个采样值，不跨序列求和或补零。
     *
     * @param series 即时查询返回的序列集合
     * @return 最新采样值；无序列、无点或点值为空时为空
     */
    private Double lastValue(List<SeriesResponse> series) {
        if (series.isEmpty() || series.getFirst().points().isEmpty()) {
            return null;
        }
        return series.getFirst().points().getLast().value();
    }

    /**
     * 按错误数、有效结果数及部分标志区分不可用、部分可用、空结果和可用。
     *
     * @param errors 失败查询数量
     * @param available 有效结果数量
     * @param partial 是否有结果报告部分数据不可用或截断
     * @return 聚合后的数据源状态
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
     * 关联面板定义及已提交的各项异步查询。
     *
     * @param definition 当前面板定义
     * @param futures 已提交的面板查询任务
     */
    private record PendingPanel(
            PanelDefinition definition, List<CompletableFuture<PrometheusClient.QueryResult>> futures) {}

    /**
     * 保存单面板展示响应及整体状态汇总所需计数。
     *
     * @param response 当前面板展示响应
     * @param errors 失败查询数量
     * @param available 存在任意序列时为 1，否则为 0
     * @param partial 是否有结果报告部分数据不可用或截断
     */
    private record PanelAggregate(PanelResponse response, int errors, int available, boolean partial) {}

    /**
     * 绑定允许的查询时段和步长：15m/15秒、1h/30秒、6h/120秒、24h/300秒。
     *
     * @param id 固定时间范围标识
     * @param duration 查询窗口时长
     * @param stepSeconds 范围查询采样步长，单位秒
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
         * 只接受四个固定范围标识，未知值抛出 400 校验异常。
         *
         * @param candidate 请求中的时间范围标识
         * @return 带固定时长和步长的查询范围
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
