package com.onehostel.gateway.helper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.util.AntPathMatcher;

import com.onehostel.common.helper.PrivTokens;
import com.onehostel.gateway.dto.PrivilegeMatrix;

/** Decides whether a caller's roles allow one API call. */
public final class PrivilegeCheck {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private PrivilegeCheck() {
    }

    /** The privileges whose APIs include this call. */
    public static List<PrivilegeMatrix.Entry> covering(PrivilegeMatrix matrix, String method, String path) {
        return matrix.privileges().stream()
                .filter(entry -> entry.apis().stream().anyMatch(api -> matches(api, method, path)))
                .toList();
    }

    /**
     * Allowed when any role's privToken has the bit of any privilege covering the call. A call no privilege
     * covers is refused: an endpoint is reachable only once a privilege is registered for it.
     */
    public static boolean allows(PrivilegeMatrix matrix, Collection<String> roles, String method, String path) {
        List<PrivilegeMatrix.Entry> covering = covering(matrix, method, path);
        if (covering.isEmpty()) {
            return false;
        }
        String token = PrivTokens.union(roles.stream().map(matrix.roleTokens()::get).filter(Objects::nonNull).toList());
        return covering.stream().anyMatch(entry -> PrivTokens.hasBit(token, entry.bit()));
    }

    private static boolean matches(PrivilegeMatrix.Api api, String method, String path) {
        boolean methodMatches = "*".equals(api.method()) || (api.method() != null && api.method().equalsIgnoreCase(method));
        return methodMatches && api.path() != null && MATCHER.match(api.path(), path);
    }
}
