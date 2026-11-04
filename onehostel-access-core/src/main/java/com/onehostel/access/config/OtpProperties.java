package com.onehostel.access.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.otp.*
 *
 * @param devMode           log codes and return them as devOtp instead of sending them (local profile only)
 * @param acceptAnyCode     accept ANY 6-digit code from this machine: a full login bypass, local profile only
 * @param expiry            how long a code is valid
 * @param maxAttempts       wrong codes allowed before the challenge is burnt
 * @param maxSendsPerWindow codes one identifier may request per sendWindow
 */
@ConfigurationProperties("app.otp")
public record OtpProperties(
        @DefaultValue("false") boolean devMode,
        @DefaultValue("false") boolean acceptAnyCode,
        @DefaultValue("5m") Duration expiry,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("3") int maxSendsPerWindow,
        @DefaultValue("15m") Duration sendWindow) {
}
