package com.onehostel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.config.AppProperties;

class AuthRateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-10-13T10:00:00Z");

    @Test
    void allowsTheLimitThenAsksToWaitUntilTheWindowEnds() {
        AuthRateLimiter limiter = new AuthRateLimiter(3);

        assertThat(limiter.tryAcquire("1.2.3.4", T0)).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(1))).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(2))).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(20))).isEqualTo(40);

        assertThat(limiter.tryAcquire("5.6.7.8", T0.plusSeconds(20))).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4", T0.plusSeconds(60))).isZero();
    }

    @Test
    void filterAnswers429WithRetryAfterOnRateLimitedPathsOnly() throws Exception {
        AppProperties properties = new AppProperties(new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Jwt("s", Duration.ofMinutes(15), Duration.ofDays(7), "i"), null,
                new AppProperties.RateLimit(1));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties,
                new SecurityErrorWriter(new ObjectMapper().findAndRegisterModules()));

        assertThat(run(filter, "/api/v1/auth/login").getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = run(filter, "/api/v1/auth/login");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getContentAsString()).contains("TOO_MANY_REQUESTS");

        assertThat(run(filter, "/api/v1/auth/logout").getStatus()).isEqualTo(200);
        assertThat(run(filter, "/api/v1/hostels/getall").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse run(AuthRateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
