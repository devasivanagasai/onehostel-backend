package com.onehostel.tenancy.config;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.onehostel.tenancy.filter.TenantResolutionFilter;
import com.onehostel.tenancy.helper.CurrentTenantArgumentResolver;
import com.onehostel.tenancy.helper.TenantTaskDecorator;

@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class TenancyWebAutoConfiguration {

    /** Order of TenantResolutionFilter: after correlation id (+2) and the internal-secret check (+10). */
    public static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    @Bean
    FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilter() {
        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>(new TenantResolutionFilter());
        registration.setOrder(FILTER_ORDER);
        return registration;
    }

    @Bean
    TenantTaskDecorator tenantTaskDecorator() {
        return new TenantTaskDecorator();
    }

    @Bean
    WebMvcConfigurer currentTenantWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentTenantArgumentResolver());
            }
        };
    }
}
