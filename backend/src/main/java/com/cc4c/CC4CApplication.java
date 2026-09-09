package com.cc4c;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** 启动业务与观测后端，扫描 com.cc4c 下的组件及配置属性；独立 com.cc4ctools 工具不在该扫描根下。 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CC4CApplication {

    /**
     * 将命令行参数交给 Spring Boot，创建并启动应用上下文。
     *
     * @param args 传给 Spring Boot 的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CC4CApplication.class, args);
    }
}
