package com.onehostel.gateway.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.onehostel.common.helper.CorrelationContext;

/**
 * The single CORS filter, ahead of everything (preflights are answered before authentication).
 * Downstream services set no CORS headers of their own.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilter(GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Refresh-Token", "X-Device-Id",
                "X-Organization-Id", CorrelationContext.HEADER));
        cors.setExposedHeaders(List.of(CorrelationContext.HEADER, "Retry-After"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
