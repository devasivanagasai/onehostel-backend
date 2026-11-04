package com.onehostel.gateway.helper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * The request as forwarded downstream: every client-supplied identity header removed, and the
 * gateway's own values (from the verified token) put in their place. Gateway MVC copies the headers
 * of this wrapper onto the proxied request.
 */
public class GatewayIdentityRequest extends HttpServletRequestWrapper {

    private final Set<String> hidden = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> overrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public GatewayIdentityRequest(HttpServletRequest request, Iterable<String> hiddenHeaders) {
        super(request);
        hiddenHeaders.forEach(hidden::add);
    }

    /** Sets (or replaces) a header; a null or blank value leaves it absent. */
    public GatewayIdentityRequest with(String name, String value) {
        if (value == null || value.isBlank()) {
            overrides.remove(name);
        } else {
            overrides.put(name, value);
        }
        return this;
    }

    @Override
    public String getHeader(String name) {
        if (overrides.containsKey(name)) {
            return overrides.get(name);
        }
        return hidden.contains(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (overrides.containsKey(name)) {
            return Collections.enumeration(List.of(overrides.get(name)));
        }
        return hidden.contains(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String name : Collections.list(super.getHeaderNames())) {
            if (!hidden.contains(name) && !overrides.containsKey(name)) {
                names.add(name);
            }
        }
        names.addAll(overrides.keySet());
        return Collections.enumeration(names);
    }

    @Override
    public int getIntHeader(String name) {
        String value = getHeader(name);
        return value == null ? -1 : Integer.parseInt(value.trim());
    }
}
