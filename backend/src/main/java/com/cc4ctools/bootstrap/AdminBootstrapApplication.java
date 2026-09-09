package com.cc4ctools.bootstrap;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 首个管理员引导工具的非 Web 入口，只扫描 bootstrap 工具包。 */
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
public class AdminBootstrapApplication {
    /**
     * 启动管理员引导上下文，默认关闭 Flyway、Banner 和健康组成员校验。
     *
     * @param args 命令行参数；本入口不自行解析
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
