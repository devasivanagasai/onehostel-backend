package com.onehostel.gateway.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.exception.ErrorResponseWriter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-client-IP token bucket on the public auth endpoints (login, OTP, refresh, password reset):
 * N requests per minute, then 429 TOO_MANY_REQUESTS with Retry-After. The client IP is the TCP peer;
 * X-Forwarded-For is ignored because clients can forge it. Buckets live in this replica's memory.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final List<String> paths;
    private final int perMinute;
    private final ErrorResponseWriter errorWriter;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public AuthRateLimitFilter(List<String> paths, int perMinute, ErrorResponseWriter errorWriter) {
        this.paths = List.copyOf(paths);
        this.perMinute = perMinute;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return paths.stream().noneMatch(path -> matcher.match(path, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Bucket bucket = buckets.get(request.getRemoteAddr(), ip -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            errorWriter.write(response, CommonErrorCode.TOO_MANY_REQUESTS, CommonErrorCode.TOO_MANY_REQUESTS.message());
            return;
        }
        chain.doFilter(request, response);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
