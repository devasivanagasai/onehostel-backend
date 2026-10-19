package com.onehostel.authgtwy.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.onehostel.authgtwy.config.AppProperties;
import com.onehostel.common.constants.ServiceConstants;

/**
 * Routes by path. /api/v1/auth/** and /api/v1/gateway/** are this service's own controllers;
 * everything else under /api/v1 goes to oh-access-core, after the security chain has accepted it
 * and IdentityForwarder has signed the caller onto it.
 *
 * The downstream correlation-id response header is dropped: the gateway already set its own.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> accessCoreRoute(AppProperties properties, IdentityForwarder identityForwarder) {
        return route("oh-access-core")
                .route(path(ServiceConstants.API_BASE + "/**")
                        .and(path(ServiceConstants.AUTH_BASE + "/**").negate())
                        .and(path(ServiceConstants.GATEWAY_BASE + "/**").negate()), http())
                .before(uri(properties.gateway().accessCoreUri()))
                .before(identityForwarder)
                .after(removeResponseHeader(ServiceConstants.CORRELATION_ID_HEADER))
                .build();
    }
}
