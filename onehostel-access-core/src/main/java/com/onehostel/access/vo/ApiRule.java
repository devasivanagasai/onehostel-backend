package com.onehostel.access.vo;

/**
 * One API call a privilege covers, as the gateway matches it.
 *
 * @param method HTTP method, or "*" for any
 * @param path   Ant-style path pattern, e.g. /api/v1/property/**
 */
public record ApiRule(String method, String path) {

    public static final String ANY_METHOD = "*";

    public static ApiRule any(String path) {
        return new ApiRule(ANY_METHOD, path);
    }

    public static ApiRule of(String method, String path) {
        return new ApiRule(method, path);
    }
}
