package com.cc4ctools.bootstrap;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AdminBootstrapApplication 负责离线维护工具的一项明确运行职责，并保持现有外部行为不变。
 */
@SpringBootApplication(
        scanBasePackages = "com.cc4ctools.bootstrap",
        excludeName = {
            "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
        })
/** AdminBootstrapApplication 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class AdminBootstrapApplication {
    /**
     * 启动对应命令行或 Spring Boot 进程，并把退出状态交给调用环境。
     *
     * @param args 调用方提供的 {@code args} 值
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AdminBootstrapApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.flyway.enabled", "false",
                "management.endpoint.health.validate-group-membership", "false",
                "spring.main.banner-mode", "off"));
        application.run(args);
    }
}
