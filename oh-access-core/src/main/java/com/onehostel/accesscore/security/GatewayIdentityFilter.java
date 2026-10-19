package com.onehostel.accesscore.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onehostel.common.security.GatewayIdentity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates a request from the identity oh-auth-gtwy signed onto it (GatewayIdentity). A
 * missing or bad signature leaves the request anonymous, and Spring Security then answers 401:
 * nothing reaches a controller without having passed through the gateway.
 *
 * Not a @Component, so it runs only inside the security chain (SecurityConfig creates it).
 */
@Slf4j
public class GatewayIdentityFilter extends OncePerRequestFilter {

    /** Authority of a verified anonymous call (a public endpoint the gateway let through). */
    public static final String GATEWAY_PUBLIC = "GATEWAY_PUBLIC";

    private final byte[] secret;
    private final Duration maxClockSkew;

    public GatewayIdentityFilter(String signingSecret, Duration maxClockSkew) {
        this.secret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.maxClockSkew = maxClockSkew;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        GatewayIdentity.verify(secret, maxClockSkew, request.getMethod(), request.getRequestURI(),
                request.getHeader(GatewayIdentity.USER_ID), request.getHeader(GatewayIdentity.EMAIL),
                request.getHeader(GatewayIdentity.ROLE), request.getHeader(GatewayIdentity.ORGANIZATION_ID),
                request.getHeader(GatewayIdentity.HOSTEL_ID), request.getHeader(GatewayIdentity.BED_ID),
                request.getHeader(GatewayIdentity.TIMESTAMP), request.getHeader(GatewayIdentity.SIGNATURE),
                Instant.now())
                .ifPresentOrElse(verified -> {
                    var authority = verified.user() != null
                            ? new SimpleGrantedAuthority(verified.user().role().authority())
                            : new SimpleGrantedAuthority(GATEWAY_PUBLIC);
                    Object principal = verified.user() != null ? verified.user() : GATEWAY_PUBLIC;
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, List.of(authority)));
                }, () -> {
                    if (request.getHeader(GatewayIdentity.SIGNATURE) != null) {
                        log.warn("Rejected gateway identity for {} {}: bad or expired signature",
                                request.getMethod(), request.getRequestURI());
                    }
                });
        filterChain.doFilter(request, response);
    }
}
