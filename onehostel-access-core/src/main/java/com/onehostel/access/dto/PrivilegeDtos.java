package com.onehostel.access.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.vo.ApiRule;

import jakarta.validation.constraints.NotNull;

/** Request and response records of the privilege and role endpoints. */
public final class PrivilegeDtos {

    private PrivilegeDtos() {
    }

    /** One entry of the catalogue the Angular app reads to check pages and buttons against its privToken. */
    public record PrivilegeResponse(String code, int bit, String name, String module, List<String> uiRoutes) {
    }

    public record RoleGrantsResponse(Role role, Set<String> privilegeCodes) {
    }

    public record RoleGrantsRequest(@NotNull Set<String> privilegeCodes) {
    }

    /** What the gateway needs to authorize a call: which privilege covers which API, and each role's token. */
    public record PrivilegeMatrixResponse(List<MatrixEntry> privileges, Map<String, String> roleTokens) {
    }

    public record MatrixEntry(String code, int bit, List<ApiRule> apis) {
    }
}
