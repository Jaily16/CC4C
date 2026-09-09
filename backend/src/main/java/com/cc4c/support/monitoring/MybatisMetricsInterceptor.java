package com.cc4c.support.monitoring;

import java.util.Locale;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/**
 * 在最外层 MyBatis 调用记录耗时，并用稳定的 Mapper 映射保留迁移前的业务指标分类。
 */
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
public final class MybatisMetricsInterceptor implements Interceptor {
    private final Cc4cMetrics metrics;
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    /**
     * 保存记录 MyBatis 耗时的指标适配器。
     *
     * @param metrics 受控指标适配器
     */
    public MybatisMetricsInterceptor(Cc4cMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 仅为当前线程最外层查询或更新记录耗时及结果；嵌套调用透传，并在退出时恢复深度。
     *
     * @param invocation 当前 MyBatis 执行器调用
     * @return 底层 MyBatis 调用的原始返回值
     * @throws Throwable 底层 MyBatis 调用失败时原样传播
     */
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

    /**
     * 恢复嵌套深度；回到最外层时移除 ThreadLocal。
     *
     * @param value 进入本次拦截前的线程调用深度
     */
    private void restoreDepth(int value) {
        if (value == 0) {
            depth.remove();
        } else {
            depth.set(value);
        }
    }

    /**
     * 从 statement ID 去掉末尾方法名，精确匹配五个 Mapper 的业务分类。
     *
     * @param statementId 包含 Mapper 完整类名和方法名的 statement ID
     * @return identity、catalog、community、interaction；未知 Mapper 返回 shared
     */
    private String moduleFor(String statementId) {
        int separator = statementId.lastIndexOf('.');
        String mapperName = separator < 0 ? "" : statementId.substring(0, separator);
        return switch (mapperName) {
            case "com.cc4c.mapper.UserMapper", "com.cc4c.mapper.AdministratorMapper" -> "identity";
            case "com.cc4c.mapper.CatalogMapper" -> "catalog";
            case "com.cc4c.mapper.BlogMapper" -> "community";
            case "com.cc4c.mapper.InteractionMapper" -> "interaction";
            default -> "shared";
        };
    }
}
