package com.onehostel.common.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.web.filter.OncePerRequestFilter;

import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.exception.ErrorResponseWriter;
import com.onehostel.tenancy.constants.IdentityHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects requests that did not come through the gateway (no or wrong X-Internal-Secret), so the identity
 * headers that TenantResolutionFilter and CurrentUser trust cannot be forged by calling a service directly.
 * Health probes are exempt. Order HIGHEST_PRECEDENCE + 10, before tenant resolution.
 */
public class InternalSecretFilter extends OncePerRequestFilter {

    private final byte[] secret;
    private final ErrorResponseWriter errorWriter;

    public InternalSecretFilter(String secret, ErrorResponseWriter errorWriter) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String presented = request.getHeader(IdentityHeaders.INTERNAL_SECRET);
        if (presented == null || !MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8))) {
            errorWriter.write(response, CommonErrorCode.UNAUTHORIZED, "Requests must come through the OneHostel gateway.");
            return;
        }
        chain.doFilter(request, response);
    }
}
