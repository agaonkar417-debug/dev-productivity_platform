package com.devflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. This class lives in the root package (com.devflow) on purpose:
 * Spring only scans this package and its sub-packages for @Controller, @Service, etc.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DevFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevFlowApplication.class, args);
    }
}
