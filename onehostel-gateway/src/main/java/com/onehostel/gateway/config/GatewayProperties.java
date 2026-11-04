package com.onehostel.gateway.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.gateway.*
 *
 * @param allowedOrigins     browser origins allowed by CORS (never "*")
 * @param publicPaths        paths reachable without an access token; everything else needs one
 * @param authenticatedPaths paths any signed-in user may call, no privilege needed (change password, the
 *                           privilege catalogue); every other API call needs a privilege covering it
 * @param rateLimitPaths     paths limited per client IP (login, OTP, refresh)
 * @param rateLimitPerMinute requests per minute per client IP on rateLimitPaths
 * @param privilegeMatrixUri access-core's internal endpoint listing each privilege's APIs and each role's privToken
 * @param privilegeCacheTtl  how long the gateway reuses the matrix before fetching it again
 */
@ConfigurationProperties("app.gateway")
public record GatewayProperties(
        List<String> allowedOrigins,
        List<String> publicPaths,
        List<String> authenticatedPaths,
        List<String> rateLimitPaths,
        @DefaultValue("10") int rateLimitPerMinute,
        String privilegeMatrixUri,
        @DefaultValue("60s") Duration privilegeCacheTtl) {

    public GatewayProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        authenticatedPaths = authenticatedPaths == null ? List.of() : List.copyOf(authenticatedPaths);
        rateLimitPaths = rateLimitPaths == null ? List.of() : List.copyOf(rateLimitPaths);
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("app.gateway.allowed-origins must list origins, never \"*\"");
        }
        if (privilegeMatrixUri == null || privilegeMatrixUri.isBlank()) {
            throw new IllegalArgumentException("app.gateway.privilege-matrix-uri is required");
        }
    }
}
