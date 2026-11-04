package com.onehostel.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.AntPathMatcher;

import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.exception.ErrorResponseWriter;

/**
 * Stateless resource server: every non-public request needs a Keycloak access token whose signature,
 * issuer and expiry are verified against the realm's JWKS (spring.security.oauth2.resourceserver.jwt.*).
 * Failures answer in the ApiErrorResponse shape the frontend reads.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Claim holding the realm roles as a flat list (protocol mapper "realm-roles-flat"). */
    public static final String ROLES_CLAIM = "roles";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayProperties properties,
            ErrorResponseWriter errorWriter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS is answered by the CorsFilter in front of the chain
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(properties.publicPaths().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(ignoringPublicPaths(properties))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(response, CommonErrorCode.UNAUTHORIZED, "Authentication is required."))
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(response, CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.message())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(response, CommonErrorCode.UNAUTHORIZED, "Authentication is required."))
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(response, CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.message())))
                .build();
    }

    /**
     * A stale token sent to a public endpoint (login, refresh-token) must not turn it into a 401,
     * so tokens are only read on protected paths.
     */
    private static BearerTokenResolver ignoringPublicPaths(GatewayProperties properties) {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        AntPathMatcher matcher = new AntPathMatcher();
        return request -> properties.publicPaths().stream().anyMatch(path -> matcher.match(path, request.getRequestURI()))
                ? null
                : resolver.resolve(request);
    }

    /** Roles from the "roles" claim become ROLE_* authorities. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
