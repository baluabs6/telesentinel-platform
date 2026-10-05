package com.telesentinel.correlation;

import com.telesentinel.correlation.config.CorrelationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CorrelationProperties.class)
public class CorrelationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CorrelationApplication.class, args);
    }
}
