package com.onehostel.gateway.filter;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class GlobalUserContextFilter implements GlobalFilter, Ordered {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_ORG_ID = "X-Organization-Id";
    private static final String HEADER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.getPrincipal() instanceof Jwt)
                .map(auth -> (Jwt) auth.getPrincipal())
                .map(jwt -> mutateRequestWithJwtClaims(exchange, jwt, finalCorrelationId))
                .defaultIfEmpty(mutateRequestForPublic(exchange, finalCorrelationId))
                .flatMap(mutatedExchange -> {
                    // Propagate correlation id back to the response header as well
                    mutatedExchange.getResponse().getHeaders().add(HEADER_CORRELATION_ID, finalCorrelationId);
                    return chain.filter(mutatedExchange);
                });
    }

    private ServerWebExchange mutateRequestWithJwtClaims(ServerWebExchange exchange, Jwt jwt, String correlationId) {
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String orgId = jwt.getClaimAsString("organization_id");
        List<String> roles = jwt.getClaimAsStringList("roles");

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .header(HEADER_CORRELATION_ID, correlationId);

        if (userId != null) {
            builder.header(HEADER_USER_ID, userId);
        }
        if (username != null) {
            builder.header(HEADER_USERNAME, username);
        }
        if (orgId != null) {
            builder.header(HEADER_ORG_ID, orgId);
        }
        if (roles != null && !roles.isEmpty()) {
            builder.header(HEADER_ROLES, String.join(",", roles));
        }

        return exchange.mutate().request(builder.build()).build();
    }

    private ServerWebExchange mutateRequestForPublic(ServerWebExchange exchange, String correlationId) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_CORRELATION_ID, correlationId)
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    @Override
    public int getOrder() {
        // Run immediately after authentication filter
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}