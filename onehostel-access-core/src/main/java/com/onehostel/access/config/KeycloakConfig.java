package com.onehostel.access.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class KeycloakConfig {

    /** RestClient on the JDK HttpClient with firm timeouts: a slow Keycloak must not hold request threads. */
    @Bean
    RestClient keycloakRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return builder.requestFactory(requestFactory).build();
    }
}
