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

/** 启动时加载受控观测目录，校验数量与标识约束，供查询服务选择固定 PromQL。 */
@Component
public final class ObservabilityCatalog {
    private final ObjectMapper objectMapper;
    private Catalog document;

    /**
     * 保存读取观测目录 JSON 的映射器。
     *
     * @param objectMapper 解析受控 JSON 的映射器
     */
    ObservabilityCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从类路径读取 observability/catalog.json，并校验完整目录。
     *
     * @throws IOException 类路径目录无法读取或 JSON 解析失败时抛出
     */
    @PostConstruct
    void load() throws IOException {
        try (InputStream input = new ClassPathResource("observability/catalog.json").getInputStream()) {
            document = objectMapper.readValue(input, Catalog.class);
        }
        validate(document);
    }

    /**
     * 读取目录中配置的八项总览查询。
     *
     * @return 目录中的总览查询定义
     */
    public List<QueryDefinition> overview() {
        return document.overview();
    }

    /**
     * 按完整 Dashboard ID 查找受控定义。
     *
     * @param id 目录中稳定且唯一的标识
     * @return 匹配的 Dashboard；不存在时为空
     */
    public Optional<DashboardDefinition> dashboard(String id) {
        return document.dashboards().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst();
    }

    /**
     * 读取目录中的二十条告警说明。
     *
     * @return 目录中的告警定义
     */
    public List<AlertDefinition> alerts() {
        return document.alerts();
    }

    /**
     * 验证 8 项总览、3 个 Dashboard、20 个面板、39 条面板查询及 20 条告警，并限制标识和查询数量。
     *
     * @param catalog 待验证的完整观测目录
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
     * 校验查询必填内容、唯一 ID、非空表达式和最多八个允许标签；将 ID 加入已见集合。
     *
     * @param query 待验证的查询定义
     * @param queryIds 已经登记的查询 ID 集合
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
     * 承载类路径观测目录的总览、Dashboard 与告警定义。
     *
     * @param overview 八项总览查询定义
     * @param dashboards 三个 Dashboard 定义
     * @param alerts 二十条告警定义
     */
    public record Catalog(
            List<QueryDefinition> overview, List<DashboardDefinition> dashboards, List<AlertDefinition> alerts) {}

    /**
     * 承载一个 Dashboard 的标识、标题和面板列表。
     *
     * @param id 目录中稳定且唯一的标识
     * @param title 门户展示标题
     * @param panels Dashboard 中的面板定义
     */
    public record DashboardDefinition(String id, String title, List<PanelDefinition> panels) {}

    /**
     * 承载面板的展示信息及一至四条受控查询。
     *
     * @param id 目录中稳定且唯一的标识
     * @param title 门户展示标题
     * @param unit 图表数值展示单位
     * @param queries 面板的固定查询定义
     */
    public record PanelDefinition(String id, String title, String unit, List<QueryDefinition> queries) {}

    /**
     * 定义固定 PromQL、图例模板及可返回给门户的标签白名单。
     *
     * @param id 目录中稳定且唯一的标识
     * @param title 门户展示标题
     * @param unit 图表数值展示单位
     * @param expression 受控 PromQL 表达式
     * @param legend 使用允许标签填充的图例模板
     * @param allowedLabels 可以返回门户的标签名称白名单
     */
    public record QueryDefinition(
            String id, String title, String unit, String expression, String legend, List<String> allowedLabels) {}

    /**
     * 承载告警标识、展示说明和严重级别。
     *
     * @param id 目录中稳定且唯一的标识
     * @param title 门户展示标题
     * @param description 告警展示说明
     * @param severity 告警严重级别
     */
    public record AlertDefinition(String id, String title, String description, String severity) {}
}
