package com.cc4ctools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PasswordMigrationApplication 负责离线维护工具的一项明确运行职责，并保持现有外部行为不变。
 */
@SpringBootApplication(
        scanBasePackages = "com.cc4ctools",
        excludeName = {
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
        })
/** PasswordMigrationApplication 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class PasswordMigrationApplication {
    /**
     * 启动对应命令行或 Spring Boot 进程，并把退出状态交给调用环境。
     *
     * @param args 调用方提供的 {@code args} 值
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PasswordMigrationApplication.class);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        application.run(args);
    }
}
