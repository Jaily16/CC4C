package com.cc4c.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.cc4c.support.monitoring.Cc4cMetrics;
import com.cc4c.support.monitoring.MybatisMetricsInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PersistenceConfiguration 负责组装运行时基础设施，并明确其边界和故障处理策略。
 */
@Configuration
public class PersistenceConfiguration {

    /**
     * 执行 PersistenceConfiguration 中的 mybatisPlusInterceptor 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        pagination.setOverflow(false);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 执行 PersistenceConfiguration 中的 mybatisMetricsInterceptor 职责，并保持既有权限、事务与副作用边界。
     *
     * @param metrics 调用方提供的 {@code metrics} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Bean
    MybatisMetricsInterceptor mybatisMetricsInterceptor(Cc4cMetrics metrics) {
        return new MybatisMetricsInterceptor(metrics);
    }
}
