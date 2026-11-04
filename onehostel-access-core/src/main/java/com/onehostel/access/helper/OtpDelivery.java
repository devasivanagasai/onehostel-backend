package com.onehostel.access.helper;

import org.springframework.stereotype.Component;

import com.onehostel.access.config.OtpProperties;
import com.onehostel.access.constants.AccessEnums.OtpChannel;
import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.exception.ErrorCode;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends one-time codes. SMS and email will go through onehostel-external-core (the single outbound
 * integration hub) once it exists; until then dev mode logs the code, and outside dev mode sending fails
 * loudly instead of silently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpDelivery {

    private final OtpProperties properties;

    @PostConstruct
    void announceMode() {
        if (properties.devMode()) {
            log.warn("OTP DEV MODE is ON: codes are logged and returned as devOtp. Never enable this in production.");
        } else {
            log.warn("No SMS/email delivery is wired in yet: /otp/send will answer OTP_DELIVERY_UNAVAILABLE.");
        }
    }

    public boolean isDevMode() {
        return properties.devMode();
    }

    public void deliver(OtpChannel channel, OtpPurpose purpose, String destination, String code) {
        if (properties.devMode()) {
            log.warn("[DEV OTP] {} {} code for {}: {} (valid {})", purpose, channel, destination, code, properties.expiry());
            return;
        }
        throw ErrorCode.OTP_DELIVERY_UNAVAILABLE.raise();
    }
}
