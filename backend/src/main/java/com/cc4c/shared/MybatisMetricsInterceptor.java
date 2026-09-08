package com.cc4c.shared;

import java.util.Locale;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/**
 * MybatisMetricsInterceptor 负责共享基础设施的一项明确运行职责，并保持现有外部行为不变。
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
final class MybatisMetricsInterceptor implements Interceptor {
    private final Cc4cMetrics metrics;
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    /**
     * 创建 MybatisMetricsInterceptor 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param metrics 调用方提供的 {@code metrics} 值
     */
    MybatisMetricsInterceptor(Cc4cMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param invocation 调用方提供的 {@code invocation} 值
     * @return 当前操作产生的 Object 结果
     * @throws Throwable 当输入、数据或依赖状态不满足当前方法约束时抛出
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
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param value 待处理或存储的值
     */
    private void restoreDepth(int value) {
        if (value == 0) {
            depth.remove();
        } else {
            depth.set(value);
        }
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param statementId 目标对象的稳定标识
     * @return 按当前协议生成或读取的字符串值
     */
    private String moduleFor(String statementId) {
        for (String module : new String[] {"identity", "catalog", "community", "interaction", "moderation", "shared"}) {
            if (statementId.startsWith("com.cc4c." + module + ".")) {
                return module;
            }
        }
        return "shared";
    }
}
