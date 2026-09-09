package com.cc4ctools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 密码迁移工具的独立非 Web 入口，扫描 com.cc4ctools；主应用不扫描该工具根包。 */
@SpringBootApplication(
        scanBasePackages = "com.cc4ctools",
        excludeName = {
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
        })
public class PasswordMigrationApplication {
    /**
     * 启动非 Web Spring 上下文，由迁移 Runner 校验备份并处理历史密码。
     *
     * @param args 命令行参数；本入口不自行解析
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PasswordMigrationApplication.class);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        application.run(args);
    }
}
