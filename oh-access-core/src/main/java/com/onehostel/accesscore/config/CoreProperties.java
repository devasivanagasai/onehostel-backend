package com.onehostel.accesscore.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Typed view of the {@code app.*} settings of oh-access-core. Validated at startup. */
@Validated
@ConfigurationProperties(prefix = "app")
public record CoreProperties(@NotNull @Valid Gateway gateway, @DefaultValue Bootstrap bootstrap) {

    /**
     * {@code signingSecret} must equal the one in oh-auth-gtwy; it verifies the forwarded identity.
     * {@code maxClockSkew}: how old a signed request may be (clock difference plus network time).
     */
    public record Gateway(@NotBlank String signingSecret, @DefaultValue("60s") Duration maxClockSkew) {
    }

    /** {@code demoOrganization} (dev): create the organization the seeded demo owner and tenant belong to. */
    public record Bootstrap(@DefaultValue("false") boolean demoOrganization) {
    }
}
