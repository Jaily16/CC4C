package com.cc4c;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
/** CC4CApplication 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class CC4CApplication {

    public static void main(String[] args) {
        SpringApplication.run(CC4CApplication.class, args);
    }
}
