package com.onehostel.gateway.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.onehostel.common.config.InternalProperties;
import com.onehostel.common.exception.ErrorResponseWriter;
import com.onehostel.gateway.client.PrivilegeMatrixClient;
import com.onehostel.gateway.filter.AuthRateLimitFilter;
import com.onehostel.gateway.filter.IdentityHeaderFilter;
import com.onehostel.gateway.filter.PrivilegeFilter;

/**
 * Filter order at the edge:
 * CORS (HIGHEST) -> correlation id (+2) -> rate limit (+30) -> Spring Security (-100) -> privileges -> identity headers -> routes.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayFilterConfig {

    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(GatewayProperties properties, ErrorResponseWriter errorWriter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(
                new AuthRateLimitFilter(properties.rateLimitPaths(), properties.rateLimitPerMinute(), errorWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }

    /** After Spring Security, so the verified token is in the SecurityContext. */
    @Bean
    FilterRegistrationBean<IdentityHeaderFilter> identityHeaderFilter(InternalProperties internal, Environment environment,
            ErrorResponseWriter errorWriter) {
        if (!internal.hasSecret() && !environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException(
                    "app.internal.secret (env GATEWAY_INTERNAL_SECRET) is required outside the local profile");
        }
        FilterRegistrationBean<IdentityHeaderFilter> registration = new FilterRegistrationBean<>(
                new IdentityHeaderFilter(internal.hasSecret() ? internal.secret() : null, errorWriter));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 10);
        return registration;
    }

    /** Short timeouts: the matrix is fetched while a request waits (once per cache period). */
    @Bean
    PrivilegeMatrixClient privilegeMatrixClient(RestClient.Builder builder, GatewayProperties properties,
            InternalProperties internal) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return new PrivilegeMatrixClient(builder.requestFactory(requestFactory).build(), properties.privilegeMatrixUri(),
                internal.hasSecret() ? internal.secret() : null, properties.privilegeCacheTtl(), Clock.systemUTC());
    }

    /** After Spring Security (it needs the verified token), before the identity headers are set. */
    @Bean
    FilterRegistrationBean<PrivilegeFilter> privilegeFilter(PrivilegeMatrixClient matrixClient, GatewayProperties properties,
            ErrorResponseWriter errorWriter) {
        List<String> unrestricted = new ArrayList<>(properties.publicPaths());
        unrestricted.addAll(properties.authenticatedPaths());
        FilterRegistrationBean<PrivilegeFilter> registration = new FilterRegistrationBean<>(
                new PrivilegeFilter(matrixClient::current, unrestricted, errorWriter));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 5);
        return registration;
    }
}
