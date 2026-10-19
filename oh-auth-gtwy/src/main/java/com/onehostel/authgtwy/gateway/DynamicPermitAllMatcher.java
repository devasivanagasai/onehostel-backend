package com.onehostel.authgtwy.gateway;

import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** Spring Security's view of the public-endpoint list: a request matcher that follows every refresh. */
@Component
@RequiredArgsConstructor
public class DynamicPermitAllMatcher implements RequestMatcher {

    private final PublicEndpointRegistry registry;

    @Override
    public boolean matches(HttpServletRequest request) {
        return registry.isPublic(request);
    }
}
