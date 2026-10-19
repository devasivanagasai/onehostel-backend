package com.onehostel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Business service (organizations, hostels, ...). Not reachable from browsers: every request
 * arrives through oh-auth-gtwy, which authenticates the caller and signs their identity onto it.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AccessCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessCoreApplication.class, args);
    }
}
