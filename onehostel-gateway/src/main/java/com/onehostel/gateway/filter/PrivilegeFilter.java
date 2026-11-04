package com.onehostel.gateway.filter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.exception.ErrorResponseWriter;
import com.onehostel.gateway.config.SecurityConfig;
import com.onehostel.gateway.dto.PrivilegeMatrix;
import com.onehostel.gateway.helper.PrivilegeCheck;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs after authentication. Every API call must be covered by a privilege whose bit is set in the
 * caller's role tokens (the privilege matrix from access-core); otherwise 403. Public paths and the
 * paths any signed-in user may call are skipped.
 */
@Slf4j
public class PrivilegeFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    private final Supplier<Optional<PrivilegeMatrix>> matrix;
    private final List<String> unrestrictedPaths;
    private final ErrorResponseWriter errorWriter;
    private final AntPathMatcher matcher = new AntPathMatcher();

    /** @param unrestrictedPaths public paths plus the paths any signed-in user may call */
    public PrivilegeFilter(Supplier<Optional<PrivilegeMatrix>> matrix, List<String> unrestrictedPaths,
            ErrorResponseWriter errorWriter) {
        this.matrix = matrix;
        this.unrestrictedPaths = List.copyOf(unrestrictedPaths);
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !path.startsWith(API_PREFIX)
                || unrestrictedPaths.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Optional<PrivilegeMatrix> current = matrix.get();
        if (current.isEmpty()) {
            errorWriter.write(response, CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Access rules are unavailable right now. Please try again shortly.");
            return;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (!PrivilegeCheck.allows(current.get(), roles(), method, path)) {
            if (PrivilegeCheck.covering(current.get(), method, path).isEmpty()) {
                // Usually a new endpoint whose privilege is not in PrivilegeCatalog yet
                log.warn("No privilege covers {} {}; refused", method, path);
            }
            errorWriter.write(response, CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.message());
            return;
        }
        chain.doFilter(request, response);
    }

    private static List<String> roles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return jwt.hasClaim(SecurityConfig.ROLES_CLAIM) ? jwt.getClaimAsStringList(SecurityConfig.ROLES_CLAIM) : List.of();
        }
        return List.of();
    }
}
