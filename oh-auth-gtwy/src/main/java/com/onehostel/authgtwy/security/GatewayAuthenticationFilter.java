package com.onehostel.authgtwy.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onehostel.authgtwy.gateway.EndpointPrivilege;
import com.onehostel.authgtwy.gateway.GatewayUserCacheService;
import com.onehostel.authgtwy.gateway.PublicEndpointRegistry;
import com.onehostel.common.constants.ServiceConstants;
import com.onehostel.common.exception.ErrorCode;
import com.onehostel.common.security.AuthenticatedUser;
import com.onehostel.common.security.SecurityErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The gateway's decision for every request, before it reaches a controller or a route:
 * <ol>
 * <li>Public endpoint (PublicEndpointRegistry, DB-driven) or CORS preflight: let through, anonymous.</li>
 * <li>Otherwise the Bearer token must be valid and unexpired (JwtService): else 401.</li>
 * <li>The account must still be usable (GatewayUserCacheService): else 401.</li>
 * <li>The endpoint must be in EndpointPrivilege and the role must hold its permission: else 403.</li>
 * </ol>
 * Writes its own error bodies (the standard envelope). Expired access tokens are renewed by the
 * client through /auth/refresh-token, not inside this filter.
 *
 * Not a @Component, so it runs only inside the security chain (SecurityConfig creates it).
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PublicEndpointRegistry publicEndpoints;
    private final GatewayUserCacheService userCache;
    private final SecurityErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || publicEndpoints.isPublic(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<AuthenticatedUser> verified = bearerToken(request).flatMap(jwtService::verify);
        if (verified.isEmpty()) {
            errorWriter.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        AuthenticatedUser user = verified.get();
        if (!userCache.isActive(user.id())) {
            log.info("Refused token of user {}: account no longer active", user.id());
            errorWriter.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        Optional<EndpointPrivilege> privilege = EndpointPrivilege.resolve(request.getMethod(), request.getRequestURI());
        if (privilege.isEmpty() || !user.role().permissions().contains(privilege.get().permission())) {
            log.info("Refused {} {} for {} ({}): {}", request.getMethod(), request.getRequestURI(), user.id(),
                    user.role(), privilege.isEmpty() ? "no privilege mapping" : "missing " + privilege.get().permission());
            errorWriter.write(response, ErrorCode.FORBIDDEN);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority(user.role().authority()))));
        filterChain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(ServiceConstants.BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(ServiceConstants.BEARER_PREFIX.length()).trim());
    }
}
