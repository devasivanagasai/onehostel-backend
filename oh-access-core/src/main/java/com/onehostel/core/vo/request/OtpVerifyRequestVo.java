package com.onehostel.core.vo.request;

import com.onehostel.core.constants.GlobalConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyRequestVo {

    @NotBlank(message = "identifier is required")
    @Size(max = 254, message = "identifier cannot exceed 254 characters")
    private String identifier;

    @NotBlank(message = "otp is required")
    @Pattern(regexp = GlobalConstants.OTP_REGEX, message = "otp must be 6 digits")
    private String otp;

    @Override
    public String toString() {
        return "OtpVerifyRequestVo{identifier='" + identifier + "', otp='[PROTECTED]'}";
    }
}
