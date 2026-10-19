package com.onehostel.service.otp;

import com.onehostel.constants.CommonEnumHelper.OtpChannel;

/**
 * Delivers a one-time code. Exactly one implementation must be a bean: LoggingOtpSender in dev;
 * production needs a real SMS / email sender, or the application refuses to start.
 */
public interface OtpSender {

    /** {@code destination} is a 10-digit mobile number for SMS, an email address for EMAIL. */
    void send(OtpChannel channel, String destination, String code);
}
