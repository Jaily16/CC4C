package com.cc4c.support.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * ObservabilityCatalog 负责独立观测门户的一项明确运行职责，并保持现有外部行为不变。
 */
@Component
public final class ObservabilityCatalog {
    private final ObjectMapper objectMapper;
    private Catalog document;

    /**
     * 创建 ObservabilityCatalog 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param objectMapper 应用统一配置的 JSON 映射器
     */
    ObservabilityCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 读取观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @throws IOException 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
    @PostConstruct
    void load() throws IOException {
        try (InputStream input = new ClassPathResource("observability/catalog.json").getInputStream()) {
            document = objectMapper.readValue(input, Catalog.class);
        }
        validate(document);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 按当前方法约定返回结果集合
     */
    public List<QueryDefinition> overview() {
        return document.overview();
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param id 调用方提供的 {@code id} 值
     * @return 存在时返回目标值，否则返回空的 Optional
     */
    public Optional<DashboardDefinition> dashboard(String id) {
        return document.dashboards().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst();
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 按当前方法约定返回结果集合
     */
    public List<AlertDefinition> alerts() {
        return document.alerts();
    }

    /**
     * 校验观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param catalog 调用方提供的 {@code catalog} 值
     */
    private static void validate(Catalog catalog) {
        if (catalog == null
                || catalog.overview() == null
                || catalog.dashboards() == null
                || catalog.alerts() == null
                || catalog.overview().size() != 8
                || catalog.dashboards().size() != 3
                || catalog.alerts().size() != 20) {
            throw new IllegalStateException("Observability catalog counts are invalid");
        }
        Set<String> dashboardIds = new HashSet<>();
        Set<String> panelIds = new HashSet<>();
        Set<String> queryIds = new HashSet<>();
        int panels = 0;
        int queries = 0;
        for (DashboardDefinition dashboard : catalog.dashboards()) {
            if (!Set.of("api-jvm", "data-cache-security", "messaging").contains(dashboard.id())
                    || !dashboardIds.add(dashboard.id())
                    || dashboard.panels() == null) {
                throw new IllegalStateException("Observability dashboard identifier is invalid");
            }
            int dashboardQueries = 0;
            for (PanelDefinition panel : dashboard.panels()) {
                panels++;
                if (!panelIds.add(dashboard.id() + ":" + panel.id())
                        || panel.queries() == null
                        || panel.queries().isEmpty()
                        || panel.queries().size() > 4) {
                    throw new IllegalStateException("Observability panel definition is invalid");
                }
                for (QueryDefinition query : panel.queries()) {
                    queries++;
                    dashboardQueries++;
                    validateQuery(query, queryIds);
                }
            }
            if (dashboardQueries > 16) {
                throw new IllegalStateException("Observability dashboard query limit is exceeded");
            }
        }
        for (QueryDefinition query : catalog.overview()) {
            validateQuery(query, queryIds);
        }
        Set<String> alertIds = new HashSet<>();
        if (panels != 20 || queries != 39 || catalog.alerts().stream().anyMatch(alert -> !alertIds.add(alert.id()))) {
            throw new IllegalStateException("Observability catalog coverage is invalid");
        }
    }

    /**
     * 校验观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param query 调用方提供的 {@code query} 值
     * @param queryIds 目标对象的稳定标识
     */
    private static void validateQuery(QueryDefinition query, Set<String> queryIds) {
        if (query == null
                || query.id() == null
                || query.expression() == null
                || query.legend() == null
                || query.allowedLabels() == null
                || !queryIds.add(query.id())
                || query.expression().isBlank()
                || query.allowedLabels().size() > 8) {
            throw new IllegalStateException("Observability query definition is invalid");
        }
    }

    /**
     * Catalog 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param overview 调用方提供的 {@code overview} 值
     * @param dashboards 调用方提供的 {@code dashboards} 值
     * @param alerts 调用方提供的 {@code alerts} 值
     */
    public record Catalog(
            List<QueryDefinition> overview, List<DashboardDefinition> dashboards, List<AlertDefinition> alerts) {}

    /**
     * DashboardDefinition 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param panels 调用方提供的 {@code panels} 值
     */
    public record DashboardDefinition(String id, String title, List<PanelDefinition> panels) {}

    /**
     * PanelDefinition 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param unit 调用方提供的 {@code unit} 值
     * @param queries 调用方提供的 {@code queries} 值
     */
    public record PanelDefinition(String id, String title, String unit, List<QueryDefinition> queries) {}

    /**
     * QueryDefinition 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param unit 调用方提供的 {@code unit} 值
     * @param expression 调用方提供的 {@code expression} 值
     * @param legend 调用方提供的 {@code legend} 值
     * @param allowedLabels 调用方提供的 {@code allowedLabels} 值
     */
    public record QueryDefinition(
            String id, String title, String unit, String expression, String legend, List<String> allowedLabels) {}

    /**
     * AlertDefinition 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param description 面向用户展示的说明文本
     * @param severity 调用方提供的 {@code severity} 值
     */
    public record AlertDefinition(String id, String title, String description, String severity) {}
}
