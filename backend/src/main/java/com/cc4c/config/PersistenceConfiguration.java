package com.cc4c.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.cc4c.support.monitoring.Cc4cMetrics;
import com.cc4c.support.monitoring.MybatisMetricsInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配 MySQL 分页限制及 MyBatis 指标拦截器。 */
@Configuration
public class PersistenceConfiguration {

    /**
     * 启用 MySQL 分页插件，限制每页最多 100 条并关闭越界页自动回到首页。
     *
     * @return 包含分页插件的 MyBatis-Plus 拦截器
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
     * 将统一指标记录器注入 MyBatis 查询拦截器。
     *
     * @param metrics 统一指标记录器
     * @return 记录 Mapper 耗时及结果的拦截器
     */
    @Bean
    MybatisMetricsInterceptor mybatisMetricsInterceptor(Cc4cMetrics metrics) {
        return new MybatisMetricsInterceptor(metrics);
    }
}
