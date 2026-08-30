package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

class MybatisMetricsInterceptorTest {
    @Test
    void recordsFixedModuleCommandAndOutcomeWithoutStatementIdentifier() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MybatisMetricsInterceptor interceptor = new MybatisMetricsInterceptor(new Cc4cMetrics(registry, properties()));
        Invocation invocation =
                invocation("com.cc4c.catalog.internal.CatalogMapper.findHome", SqlCommandType.SELECT, "result");

        assertEquals("result", interceptor.intercept(invocation));
        assertEquals(
                1L,
                registry.get("cc4c.mybatis.operations")
                        .tag("module", "catalog")
                        .tag("command", "select")
                        .tag("outcome", "success")
                        .timer()
                        .count());
        assertTrue(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> tag.getValue().contains("CatalogMapper")));
    }

    @Test
    void recordsErrorAndAlwaysRestoresNestedDepth() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MybatisMetricsInterceptor interceptor = new MybatisMetricsInterceptor(new Cc4cMetrics(registry, properties()));
        Invocation failed = invocation(
                "com.cc4c.interaction.internal.InteractionMapper.updateFavorite", SqlCommandType.UPDATE, null);
        when(failed.proceed()).thenThrow(new IllegalStateException("not exported"));

        assertThrows(IllegalStateException.class, () -> interceptor.intercept(failed));
        Invocation next = invocation("com.cc4c.identity.internal.IdentityMapper.findUser", SqlCommandType.SELECT, "ok");
        assertEquals("ok", interceptor.intercept(next));
        assertEquals(
                1L,
                registry.get("cc4c.mybatis.operations")
                        .tag("module", "interaction")
                        .tag("command", "update")
                        .tag("outcome", "error")
                        .timer()
                        .count());
        assertEquals(
                1L,
                registry.get("cc4c.mybatis.operations")
                        .tag("module", "identity")
                        .tag("command", "select")
                        .tag("outcome", "success")
                        .timer()
                        .count());
    }

    private Invocation invocation(String id, SqlCommandType command, Object result) throws Throwable {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn(id);
        when(statement.getSqlCommandType()).thenReturn(command);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[] {statement});
        when(invocation.proceed()).thenReturn(result);
        return invocation;
    }

    private ObservabilityProperties properties() {
        return new ObservabilityProperties(
                true, "test", "test_observer", "fixed-test-password-at-least-24-chars", Duration.ofSeconds(15), 100);
    }
}
