package com.onehostel.authgtwy.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onehostel.authgtwy.config.AppProperties;
import com.onehostel.common.constants.ServiceConstants;
import com.onehostel.common.exception.ErrorCode;
import com.onehostel.common.security.SecurityErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP limit on the public auth endpoints, against password, passcode and OTP guessing.
 * Uses {@code getRemoteAddr()}: behind a reverse proxy, set
 * {@code server.forward-headers-strategy=native} so it is the client's IP and not the proxy's.
 * X-Forwarded-For is never read directly, since any client can forge it.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiter limiter;
    private final SecurityErrorWriter errorWriter;

    public AuthRateLimitFilter(AppProperties properties, SecurityErrorWriter errorWriter) {
        this.limiter = new AuthRateLimiter(properties.rateLimit().authRequestsPerMinute());
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !ServiceConstants.AUTH_RATE_LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        long retryAfter = limiter.tryAcquire(request.getRemoteAddr(), Instant.now());
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            errorWriter.write(response, ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
