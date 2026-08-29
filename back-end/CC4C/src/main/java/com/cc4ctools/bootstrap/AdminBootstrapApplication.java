package com.cc4ctools.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;

import java.util.Map;

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
