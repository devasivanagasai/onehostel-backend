package com.onehostel.authgtwy.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicEndpointRegistryTest {

    private PublicEndpointRepository repository;
    private PublicEndpointRegistry registry;

    @BeforeEach
    void setUp() {
        repository = mock(PublicEndpointRepository.class);
        registry = new PublicEndpointRegistry(repository);
    }

    @Test
    void builtInEndpointsArePublicBeforeTheDatabaseIsRead() {
        assertThat(isPublic("POST", "/api/v1/auth/login")).isTrue();
        assertThat(isPublic("POST", "/api/v1/auth/refresh-token")).isTrue();
        assertThat(isPublic("GET", "/actuator/health")).isTrue();
        assertThat(isPublic("GET", "/swagger-ui/index.html")).isTrue();

        assertThat(isPublic("GET", "/api/v1/auth/login")).isFalse();
        assertThat(isPublic("GET", "/api/v1/auth/me")).isFalse();
        assertThat(isPublic("POST", "/api/v1/hostels/getall")).isFalse();
    }

    @Test
    void refreshAddsStoredEndpointsWithoutARestart() {
        when(repository.findByEnabledTrue()).thenReturn(List.of(
                new PublicEndpoint("/api/v1/hostels/public/**", "GET", "Public hostel listing", false),
                new PublicEndpoint("/api/v1/ping", null, "Any method", false)));

        registry.refresh();

        assertThat(isPublic("GET", "/api/v1/hostels/public/list")).isTrue();
        assertThat(isPublic("POST", "/api/v1/hostels/public/list")).isFalse();
        assertThat(isPublic("DELETE", "/api/v1/ping")).isTrue();
        assertThat(isPublic("POST", "/api/v1/auth/login")).isTrue();
    }

    @Test
    void anInvalidStoredPatternIsSkippedNotFatal() {
        when(repository.findByEnabledTrue()).thenReturn(List.of(new PublicEndpoint("/api/{bad", "GET", "broken", false)));

        registry.refresh();

        assertThat(isPublic("POST", "/api/v1/auth/login")).isTrue();
    }

    private boolean isPublic(String method, String path) {
        return registry.isPublic(new MockHttpServletRequest(method, path));
    }
}
