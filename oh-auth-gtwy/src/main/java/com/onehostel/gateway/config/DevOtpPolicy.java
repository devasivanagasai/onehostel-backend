package com.onehostel.gateway.config;

import java.net.InetSocketAddress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Local-development switch that accepts any 6-digit OTP (onehostel.dev.accept-any-otp).
 * This is a full login bypass, so it only applies when ALL of these hold:
 * the flag is true, the "local" profile is active and "prod" is not, and the request
 * arrives over loopback (the socket address, not X-Forwarded-For, which clients can forge).
 * The application refuses to start if the flag is set outside the local profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevOtpPolicy {

    private final Environment environment;

    @Value("${onehostel.dev.accept-any-otp:false}")
    private boolean acceptAnyOtp;

    private boolean enabled;

    @PostConstruct
    void validate() {
        boolean localOnly = environment.acceptsProfiles(Profiles.of("local"))
                && !environment.acceptsProfiles(Profiles.of("prod"));
        if (acceptAnyOtp && !localOnly) {
            throw new IllegalStateException(
                    "onehostel.dev.accept-any-otp=true is only allowed with the 'local' profile; refusing to start");
        }
        enabled = acceptAnyOtp;
        if (enabled) {
            log.warn("DEV: ANY 6-digit OTP is accepted for loopback requests (onehostel.dev.accept-any-otp). Local development only.");
        }
    }

    /** True when this request may skip the OTP code comparison. */
    public boolean acceptsAnyCode(ServerHttpRequest request) {
        if (!enabled) {
            return false;
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress();
    }
}
