package com.onehostel.common.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.common.advice.RequestEnvelopeAdvice;
import com.onehostel.common.advice.ResponseEnvelopeAdvice;
import com.onehostel.common.exception.ErrorResponseWriter;
import com.onehostel.common.exception.GlobalExceptionHandler;
import com.onehostel.common.filter.CorrelationIdFilter;
import com.onehostel.common.filter.InternalSecretFilter;
import com.onehostel.common.helper.CurrentUserArgumentResolver;

/** Error handling, envelopes, correlation id, gateway-secret check and the CurrentUser resolver for every service. */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(InternalProperties.class)
public class CommonWebAutoConfiguration {

    public static final int CORRELATION_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;
    public static final int INTERNAL_SECRET_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private static final Logger log = LoggerFactory.getLogger(CommonWebAutoConfiguration.class);

    @Bean
    ErrorResponseWriter errorResponseWriter(ObjectMapper objectMapper,
            @Value("${spring.application.name:unknown}") String applicationName) {
        return new ErrorResponseWriter(objectMapper, applicationName);
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler(ErrorResponseWriter errorResponseWriter) {
        return new GlobalExceptionHandler(errorResponseWriter.sourceService());
    }

    @Bean
    RequestEnvelopeAdvice requestEnvelopeAdvice(ObjectMapper objectMapper) {
        return new RequestEnvelopeAdvice(objectMapper);
    }

    @Bean
    ResponseEnvelopeAdvice responseEnvelopeAdvice() {
        return new ResponseEnvelopeAdvice();
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(CORRELATION_FILTER_ORDER);
        return registration;
    }

    /**
     * Requires X-Internal-Secret on every request. Without a configured secret the service refuses to start,
     * except under the "local" profile, where it trusts identity headers from any caller and says so.
     */
    @Bean
    FilterRegistrationBean<InternalSecretFilter> internalSecretFilter(InternalProperties properties, Environment environment,
            ErrorResponseWriter errorResponseWriter) {

        boolean enabled = properties.verifyInbound();
        if (enabled && !properties.hasSecret()) {
            if (!environment.acceptsProfiles(Profiles.of("local"))) {
                throw new IllegalStateException(
                        "app.internal.secret (env GATEWAY_INTERNAL_SECRET) is required outside the local profile");
            }
            log.warn("app.internal.secret is not set: identity headers are trusted from ANY caller (local profile only)");
            enabled = false;
        }
        FilterRegistrationBean<InternalSecretFilter> registration = new FilterRegistrationBean<>(
                new InternalSecretFilter(properties.hasSecret() ? properties.secret() : "", errorResponseWriter));
        registration.setOrder(INTERNAL_SECRET_FILTER_ORDER);
        registration.setEnabled(enabled);
        return registration;
    }

    @Bean
    WebMvcConfigurer currentUserWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }
}
