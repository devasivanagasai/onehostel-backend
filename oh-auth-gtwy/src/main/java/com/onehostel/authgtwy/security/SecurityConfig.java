package com.onehostel.authgtwy.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

import com.onehostel.authgtwy.gateway.DynamicPermitAllMatcher;
import com.onehostel.authgtwy.gateway.GatewayUserCacheService;
import com.onehostel.authgtwy.gateway.PublicEndpointRegistry;
import com.onehostel.common.security.RestAccessDeniedHandler;
import com.onehostel.common.security.RestAuthenticationEntryPoint;
import com.onehostel.common.security.SecurityErrorWriter;

import lombok.RequiredArgsConstructor;

/**
 * The gateway's single security chain, for its own endpoints and for every routed request:
 * <pre>
 * CorrelationIdFilter (servlet filter, first) → CORS → AuthRateLimitFilter → GatewayAuthenticationFilter
 *   → /api/v1/auth/** and /api/v1/gateway/** controllers, or the route to oh-access-core
 * </pre>
 * Rate limiting and authentication run after CORS, so their 429/401/403 bodies carry CORS headers
 * and the browser can read them. GatewayAuthenticationFilter makes the actual decisions; the rules
 * below are the backstop.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final JwtService jwtService;
    private final PublicEndpointRegistry publicEndpoints;
    private final DynamicPermitAllMatcher permitAllMatcher;
    private final GatewayUserCacheService userCache;
    private final SecurityErrorWriter errorWriter;
    private final AuthRateLimitFilter authRateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Access tokens travel in a header, not a cookie. The one cookie (refresh) is SameSite=Strict.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(authRateLimitFilter, CorsFilter.class)
                .addFilterBefore(new GatewayAuthenticationFilter(jwtService, publicEndpoints, userCache, errorWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(permitAllMatcher).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /** The rate limiter runs inside the chain above; this stops Boot from also running it as a servlet filter. */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
