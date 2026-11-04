package com.onehostel.tenancy.filter;

import java.io.IOException;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onehostel.tenancy.constants.IdentityHeaders;
import com.onehostel.tenancy.helper.TenantContext.Restore;
import com.onehostel.tenancy.helper.TenantContext.Scope;
import com.onehostel.tenancy.helper.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Populates TenantContext from the gateway's identity headers for the duration of the request
 * and always clears it afterwards. The only class that reads the organization headers.
 *
 * <ul>
 *   <li>organization header present: scoped to it (and the parent chain)</li>
 *   <li>SUPER_ADMIN without one: unscoped, sees every organization</li>
 *   <li>neither (public endpoints such as login): no tenant; tenant collections then fail closed</li>
 * </ul>
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    /** MDC key for the log pattern: %X{tenantId}. "*" = unscoped (super admin), "-" = no tenant. */
    public static final String MDC_TENANT = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Scope scope = resolve(request);
        try (Restore ignored = TenantContext.enter(scope);
             MDC.MDCCloseable mdc = MDC.putCloseable(MDC_TENANT, scope == null ? "-" : scope.isUnscoped() ? "*" : scope.tenantId())) {
            chain.doFilter(request, response);
        }
    }

    private static Scope resolve(HttpServletRequest request) {
        String organizationId = request.getHeader(IdentityHeaders.ORGANIZATION_ID);
        if (organizationId != null && !organizationId.isBlank()) {
            List<String> parents = IdentityHeaders.splitList(request.getHeader(IdentityHeaders.PARENT_ORG_IDS));
            return Scope.of(organizationId.trim(), parents);
        }
        List<String> roles = IdentityHeaders.splitList(request.getHeader(IdentityHeaders.USER_ROLES));
        if (roles.contains(IdentityHeaders.SUPER_ADMIN_ROLE)) {
            return Scope.unscoped("platform super admin");
        }
        return null;
    }
}
