package com.cc4c.shared;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/** PersistenceConfiguration 负责组装运行时基础设施，并明确其边界和故障处理策略。 */
public class PersistenceConfiguration {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        pagination.setOverflow(false);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    MybatisMetricsInterceptor mybatisMetricsInterceptor(Cc4cMetrics metrics) {
        return new MybatisMetricsInterceptor(metrics);
    }
}
