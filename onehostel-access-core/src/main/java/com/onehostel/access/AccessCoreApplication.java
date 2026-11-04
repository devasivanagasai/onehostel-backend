package com.onehostel.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AccessCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessCoreApplication.class, args);
    }
}
