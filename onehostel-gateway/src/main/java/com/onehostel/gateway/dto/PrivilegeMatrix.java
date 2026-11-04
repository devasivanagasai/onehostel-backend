package com.onehostel.gateway.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * access-core's privilege matrix (GET /internal/v1/privileges/matrix): the APIs each privilege bit covers,
 * and each role's privToken.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivilegeMatrix(List<Entry> privileges, Map<String, String> roleTokens) {

    public PrivilegeMatrix {
        privileges = privileges == null ? List.of() : List.copyOf(privileges);
        roleTokens = roleTokens == null ? Map.of() : Map.copyOf(roleTokens);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String code, int bit, List<Api> apis) {

        public Entry {
            apis = apis == null ? List.of() : List.copyOf(apis);
        }
    }

    /** @param method an HTTP method, or "*" for any */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Api(String method, String path) {
    }
}
