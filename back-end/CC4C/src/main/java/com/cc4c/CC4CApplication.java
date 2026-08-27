package com.cc4c;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CC4CApplication {

    public static void main(String[] args) {
        SpringApplication.run(CC4CApplication.class, args);
    }

}
