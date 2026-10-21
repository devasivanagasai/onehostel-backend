package com.onehostel.core.vo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.core.constants.EnumHelper.OtpChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtpSendResponseVo {

    private int expiresInSeconds;

    /** Channel the code was sent through (derived from the identifier). */
    private OtpChannel channel;

    /** The code itself; only present when the gateway runs in OTP dev mode. */
    private String devOtp;
}
