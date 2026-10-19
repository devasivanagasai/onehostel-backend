package com.onehostel.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Typed view of the {@code app.*} settings. Validated at startup, so a missing
 * required value stops the application instead of failing later at runtime.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull @Valid Cors cors, @NotNull @Valid Jwt jwt, @Valid Bootstrap bootstrap,
        @DefaultValue @Valid RateLimit rateLimit) {

    /** Origins allowed to call the API from a browser, e.g. the Angular app. */
    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /**
     * Token signing. {@code secret} is Base64 and must decode to at least 32 bytes (HS256);
     * JwtService checks the length at startup.
     */
    public record Jwt(@NotBlank String secret, @NotNull Duration expiration, @NotNull Duration refreshExpiration,
            @NotBlank String issuer) {
    }

    /**
     * Optional first admin account, created at startup if no user has that email yet.
     * Leave both values empty to skip. The password is only read once, then hashed.
     * {@code demoUsers} (dev only) also creates one demo account per role, see UserSeeder.
     */
    public record Bootstrap(String adminEmail, String adminUsername, String adminPassword, String adminPasscode,
            @DefaultValue("Administrator") String adminName, boolean demoUsers,
            @DefaultValue("true") boolean userMigration) {

        public boolean enabled() {
            return adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank();
        }
    }

    /** Requests per minute per client IP on the public auth endpoints (AuthRateLimitFilter). */
    public record RateLimit(@DefaultValue("10") @Min(1) int authRequestsPerMinute) {
    }
}
