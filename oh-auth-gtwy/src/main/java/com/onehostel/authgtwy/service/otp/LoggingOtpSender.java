package com.onehostel.service.otp;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.onehostel.constants.CommonEnumHelper.OtpChannel;

import lombok.extern.slf4j.Slf4j;

/** Dev only: writes the code to the server log instead of sending it. Never active in prod. */
@Slf4j
@Component
@Profile("dev")
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(OtpChannel channel, String destination, String code) {
        log.warn("DEV OTP via {} to {}: {}", channel, destination, code);
    }
}
