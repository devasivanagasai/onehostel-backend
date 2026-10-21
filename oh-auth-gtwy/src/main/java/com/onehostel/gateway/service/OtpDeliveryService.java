package com.onehostel.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.onehostel.core.constants.EnumHelper.OtpChannel;
import com.onehostel.core.constants.EnumHelper.OtpPurpose;
import com.onehostel.core.exception.ErrorCode;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Delivers one-time codes. In dev mode (onehostel.otp.dev-mode=true, set by the "local" profile)
 * the code is written to the console and returned to the caller as devOtp. No SMS or email
 * provider is wired in yet, so outside dev mode sending fails loudly instead of silently.
 */
@Slf4j
@Service
public class OtpDeliveryService {

    @Value("${onehostel.otp.dev-mode:false}")
    private boolean devMode;

    @PostConstruct
    void announceMode() {
        if (devMode) {
            log.warn("OTP DEV MODE is ON: codes are logged and returned in API responses. Never enable this in production.");
        } else {
            log.warn("OTP dev mode is off and no SMS/email provider is configured: /otp/send will fail.");
        }
    }

    public boolean isDevMode() {
        return devMode;
    }

    public Mono<Void> deliver(OtpChannel channel, OtpPurpose purpose, String destination, String code, int expiresInSeconds) {
        if (devMode) {
            log.warn("[DEV OTP] {} {} code for {}: {} (valid {} s)", purpose, channel, destination, code, expiresInSeconds);
            return Mono.empty();
        }
        log.error("Cannot deliver {} OTP via {}: no SMS/email provider is configured", purpose, channel);
        return Mono.error(ErrorCode.INTERNAL_ERROR.raiseWithMessage("Sending codes is not available right now. Please log in with your password."));
    }
}
