package com.cc4c;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CC4CApplication 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CC4CApplication {

    /**
     * 启动 CC4C Spring Boot 进程并交由框架完成组件装配。
     *
     * @param args 调用方提供的 {@code args} 值
     */
    public static void main(String[] args) {
        SpringApplication.run(CC4CApplication.class, args);
    }
}
