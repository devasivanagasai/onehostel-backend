package com.onehostel.access.vo;

import java.util.List;
import java.util.Set;

import com.onehostel.access.constants.AccessEnums.Role;

/**
 * A privilege as declared in code (PrivilegeCatalog). Its bit is not declared here: it is assigned once,
 * when the privilege is first stored, and never changes or gets reused.
 *
 * @param code         stable name, e.g. ROOMS_MANAGE; the Angular app and role grants refer to it
 * @param module       the screen or area it belongs to, for grouping in the role editor
 * @param uiRoutes     app pages it opens (the Angular router checks these)
 * @param apis         API calls it allows (the gateway checks these)
 * @param defaultRoles roles granted it when it first appears
 */
public record PrivilegeDefinition(String code, String name, String module, List<String> uiRoutes, List<ApiRule> apis,
        Set<Role> defaultRoles) {

    public PrivilegeDefinition {
        uiRoutes = List.copyOf(uiRoutes);
        apis = List.copyOf(apis);
        defaultRoles = Set.copyOf(defaultRoles);
    }
}
