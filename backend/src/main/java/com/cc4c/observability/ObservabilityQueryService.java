package com.cc4c.observability;

import com.cc4c.observability.ObservabilityCatalog.AlertDefinition;
import com.cc4c.observability.ObservabilityCatalog.DashboardDefinition;
import com.cc4c.observability.ObservabilityCatalog.PanelDefinition;
import com.cc4c.observability.ObservabilityCatalog.QueryDefinition;
import com.cc4c.observability.ObservabilityDtos.AlertRuleResponse;
import com.cc4c.observability.ObservabilityDtos.AlertsResponse;
import com.cc4c.observability.ObservabilityDtos.DashboardResponse;
import com.cc4c.observability.ObservabilityDtos.MetricCardResponse;
import com.cc4c.observability.ObservabilityDtos.OverviewResponse;
import com.cc4c.observability.ObservabilityDtos.PanelResponse;
import com.cc4c.observability.ObservabilityDtos.PanelStatus;
import com.cc4c.observability.ObservabilityDtos.SeriesResponse;
import com.cc4c.observability.ObservabilityDtos.SourceStatus;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
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

/** ObservabilityQueryService 把固定 catalog 查询聚合为有界、可降级的中文接口响应。 */
@Service
public final class ObservabilityQueryService {
    private final ObservabilityCatalog catalog;
    private final PrometheusClient prometheus;

    ObservabilityQueryService(ObservabilityCatalog catalog, PrometheusClient prometheus) {
        this.catalog = catalog;
        this.prometheus = prometheus;
    }

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

    private Double lastValue(List<SeriesResponse> series) {
        if (series.isEmpty() || series.getFirst().points().isEmpty()) {
            return null;
        }
        return series.getFirst().points().getLast().value();
    }

    private SourceStatus sourceStatus(int errors, int available, boolean partial) {
        if (errors > 0 && available == 0) {
            return SourceStatus.UNAVAILABLE;
        }
        if (errors > 0 || partial) {
            return SourceStatus.PARTIAL;
        }
        return available == 0 ? SourceStatus.EMPTY : SourceStatus.AVAILABLE;
    }

    private record PendingPanel(
            PanelDefinition definition, List<CompletableFuture<PrometheusClient.QueryResult>> futures) {}

    private record PanelAggregate(PanelResponse response, int errors, int available, boolean partial) {}

    private record Range(String id, Duration duration, long stepSeconds) {
        private static final Map<String, Range> ALLOWED = new LinkedHashMap<>();

        static {
            ALLOWED.put("15m", new Range("15m", Duration.ofMinutes(15), 15));
            ALLOWED.put("1h", new Range("1h", Duration.ofHours(1), 30));
            ALLOWED.put("6h", new Range("6h", Duration.ofHours(6), 120));
            ALLOWED.put("24h", new Range("24h", Duration.ofHours(24), 300));
        }

        static Range parse(String candidate) {
            Range range = ALLOWED.get(candidate);
            if (range == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, BusinessCode.VALIDATION_ERROR, "观测时间范围无效");
            }
            return range;
        }
    }
}
