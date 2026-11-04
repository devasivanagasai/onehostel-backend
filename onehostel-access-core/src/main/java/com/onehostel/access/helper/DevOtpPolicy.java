package com.onehostel.access.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.onehostel.access.config.OtpProperties;
import com.onehostel.access.vo.ClientInfo;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Local-development switch that accepts any 6-digit code (app.otp.accept-any-code). A full login bypass,
 * so it applies only when ALL hold: the flag is on, the "local" profile is active and "prod" is not, and the
 * client is this machine (the address the gateway saw on the socket). The service refuses to start if the
 * flag is set outside the local profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevOtpPolicy {

    private final OtpProperties properties;
    private final Environment environment;

    @PostConstruct
    void validate() {
        boolean localOnly = environment.acceptsProfiles(Profiles.of("local")) && !environment.acceptsProfiles(Profiles.of("prod"));
        if ((properties.acceptAnyCode() || properties.devMode()) && !localOnly) {
            throw new IllegalStateException("app.otp.accept-any-code and app.otp.dev-mode are only allowed with the 'local' profile");
        }
        if (properties.acceptAnyCode()) {
            log.warn("DEV: ANY 6-digit OTP is accepted for requests from this machine (app.otp.accept-any-code).");
        }
    }

    public boolean acceptsAnyCode(ClientInfo client) {
        if (!properties.acceptAnyCode() || client.ipAddress() == null) {
            return false;
        }
        try {
            return InetAddress.getByName(client.ipAddress()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
