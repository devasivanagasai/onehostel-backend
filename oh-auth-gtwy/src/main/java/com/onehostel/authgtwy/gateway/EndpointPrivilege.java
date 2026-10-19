package com.onehostel.authgtwy.gateway;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.onehostel.common.constants.CommonEnumHelper.Permission;
import com.onehostel.common.constants.ServiceConstants;

/**
 * The gateway's authoritative map from a protected endpoint to the permission it needs. The first
 * matching entry wins, so list specific paths before broad ones. A protected request that matches
 * no entry is refused (403): a new endpoint is unreachable until it is added here.
 *
 * Public endpoints (PublicEndpointRegistry) are checked before this map and never reach it.
 */
public enum EndpointPrivilege {

    AUTH_ME(HttpMethod.GET, Permission.ACCOUNT_SELF, ServiceConstants.AUTH_BASE + ServiceConstants.AUTH_ME),

    GATEWAY_ADMIN(null, Permission.GATEWAY_MANAGE, ServiceConstants.GATEWAY_BASE + "/**"),

    ADMIN_AREA(null, Permission.ADMIN_AREA, ServiceConstants.ADMIN_BASE + "/**"),
    OWNER_AREA(null, Permission.OWNER_AREA, ServiceConstants.OWNER_BASE + "/**"),
    TENANT_AREA(null, Permission.TENANT_AREA, ServiceConstants.TENANT_BASE + "/**"),

    ORGANIZATION_READ(HttpMethod.POST, Permission.ORGANIZATION_READ,
            ServiceConstants.ORGANIZATION_BASE + ServiceConstants.ACTION_INQUIRE,
            ServiceConstants.ORGANIZATION_BASE + ServiceConstants.ACTION_GET_ALL),
    ORGANIZATION_MANAGE(HttpMethod.POST, Permission.ORGANIZATION_MANAGE,
            ServiceConstants.ORGANIZATION_BASE + ServiceConstants.ACTION_CREATE,
            ServiceConstants.ORGANIZATION_BASE + ServiceConstants.ACTION_UPDATE,
            ServiceConstants.ORGANIZATION_BASE + ServiceConstants.ACTION_DELETE),

    HOSTEL_READ(HttpMethod.POST, Permission.HOSTEL_READ,
            ServiceConstants.HOSTEL_BASE + ServiceConstants.ACTION_INQUIRE,
            ServiceConstants.HOSTEL_BASE + ServiceConstants.ACTION_GET_ALL),
    HOSTEL_CREATE(HttpMethod.POST, Permission.HOSTEL_CREATE, ServiceConstants.HOSTEL_BASE + ServiceConstants.ACTION_CREATE),
    HOSTEL_UPDATE(HttpMethod.POST, Permission.HOSTEL_UPDATE, ServiceConstants.HOSTEL_BASE + ServiceConstants.ACTION_UPDATE),
    HOSTEL_DELETE(HttpMethod.POST, Permission.HOSTEL_DELETE, ServiceConstants.HOSTEL_BASE + ServiceConstants.ACTION_DELETE);

    private final HttpMethod method;
    private final Permission permission;
    private final List<PathPattern> patterns;

    EndpointPrivilege(HttpMethod method, Permission permission, String... patterns) {
        this.method = method;
        this.permission = permission;
        this.patterns = Arrays.stream(patterns).map(PathPatternParser.defaultInstance::parse).toList();
    }

    public Permission permission() {
        return permission;
    }

    /** The first entry covering this request, if any. {@code method} null in an entry means any method. */
    public static Optional<EndpointPrivilege> resolve(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        return Arrays.stream(values())
                .filter(entry -> entry.method == null || entry.method.matches(method))
                .filter(entry -> entry.patterns.stream().anyMatch(pattern -> pattern.matches(container)))
                .findFirst();
    }
}
