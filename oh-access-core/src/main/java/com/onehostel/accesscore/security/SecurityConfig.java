package com.onehostel.accesscore.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.onehostel.accesscore.config.CoreProperties;
import com.onehostel.common.constants.CommonEnumHelper.Role;
import com.onehostel.common.constants.ServiceConstants;
import com.onehostel.common.security.RestAccessDeniedHandler;
import com.onehostel.common.security.RestAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

/**
 * Stateless, and trusting only the gateway: every request needs a valid gateway signature
 * (GatewayIdentityFilter). No CORS here: browsers never call this service, and CORS headers
 * from both services would be duplicated and rejected by the browser.
 *
 * The gateway already enforces endpoint privileges; the role areas are repeated here as a second
 * line of defence, and tenancy is enforced by the services.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CoreProperties properties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new GatewayIdentityFilter(properties.gateway().signingSecret(),
                        properties.gateway().maxClockSkew()), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(ServiceConstants.HEALTH_PATH).permitAll()
                        .requestMatchers(ServiceConstants.SWAGGER_PATHS).permitAll()
                        .requestMatchers(ServiceConstants.ADMIN_BASE + "/**").hasRole(Role.SUPER_ADMIN.name())
                        .requestMatchers(ServiceConstants.OWNER_BASE + "/**").hasRole(Role.HOSTEL_OWNER.name())
                        .requestMatchers(ServiceConstants.TENANT_BASE + "/**").hasRole(Role.HOSTEL_TENANT.name())
                        .anyRequest().authenticated());
        return http.build();
    }
}
