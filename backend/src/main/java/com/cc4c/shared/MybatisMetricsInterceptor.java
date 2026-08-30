package com.cc4c.shared;

import java.util.Locale;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

@Intercepts({
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {
                MappedStatement.class,
                Object.class,
                org.apache.ibatis.session.RowBounds.class,
                org.apache.ibatis.session.ResultHandler.class
            }),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {
                MappedStatement.class,
                Object.class,
                org.apache.ibatis.session.RowBounds.class,
                org.apache.ibatis.session.ResultHandler.class,
                org.apache.ibatis.cache.CacheKey.class,
                org.apache.ibatis.mapping.BoundSql.class
            })
})
final class MybatisMetricsInterceptor implements Interceptor {
    private final Cc4cMetrics metrics;
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    MybatisMetricsInterceptor(Cc4cMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        int currentDepth = depth.get();
        depth.set(currentDepth + 1);
        if (currentDepth > 0) {
            try {
                return invocation.proceed();
            } finally {
                restoreDepth(currentDepth);
            }
        }

        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        String module = moduleFor(statement.getId());
        String command = statement.getSqlCommandType().name().toLowerCase(Locale.ROOT);
        long startedNanos = metrics.start();
        try {
            Object result = invocation.proceed();
            metrics.record(
                    "cc4c.mybatis.operations",
                    startedNanos,
                    "module",
                    module,
                    "command",
                    command,
                    "outcome",
                    "success");
            return result;
        } catch (Throwable throwable) {
            metrics.record(
                    "cc4c.mybatis.operations", startedNanos, "module", module, "command", command, "outcome", "error");
            throw throwable;
        } finally {
            restoreDepth(currentDepth);
        }
    }

    private void restoreDepth(int value) {
        if (value == 0) {
            depth.remove();
        } else {
            depth.set(value);
        }
    }

    private String moduleFor(String statementId) {
        for (String module : new String[] {"identity", "catalog", "community", "interaction", "moderation", "shared"}) {
            if (statementId.startsWith("com.cc4c." + module + ".")) {
                return module;
            }
        }
        return "shared";
    }
}
