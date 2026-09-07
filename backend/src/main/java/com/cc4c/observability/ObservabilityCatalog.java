package com.cc4c.observability;

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

/** ObservabilityCatalog 加载并验证唯一固定指标目录，阻止运行时接受任意 PromQL。 */
@Component
public final class ObservabilityCatalog {
    private final ObjectMapper objectMapper;
    private Catalog document;

    ObservabilityCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        try (InputStream input = new ClassPathResource("observability/catalog.json").getInputStream()) {
            document = objectMapper.readValue(input, Catalog.class);
        }
        validate(document);
    }

    public List<QueryDefinition> overview() {
        return document.overview();
    }

    public Optional<DashboardDefinition> dashboard(String id) {
        return document.dashboards().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst();
    }

    public List<AlertDefinition> alerts() {
        return document.alerts();
    }

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

    /** Catalog 是磁盘中固定观测目录的根结构。 */
    public record Catalog(
            List<QueryDefinition> overview, List<DashboardDefinition> dashboards, List<AlertDefinition> alerts) {}

    /** DashboardDefinition 描述一个固定页面及其有限面板。 */
    public record DashboardDefinition(String id, String title, List<PanelDefinition> panels) {}

    /** PanelDefinition 描述固定标题、单位及最多四条查询。 */
    public record PanelDefinition(String id, String title, String unit, List<QueryDefinition> queries) {}

    /** QueryDefinition 保存仅服务端可见的 PromQL 和允许出站的标签。 */
    public record QueryDefinition(
            String id, String title, String unit, String expression, String legend, List<String> allowedLabels) {}

    /** AlertDefinition 为一条既有 Prometheus 告警提供中文元数据。 */
    public record AlertDefinition(String id, String title, String description, String severity) {}
}
