package com.onehostel.core.vo.request;

import com.onehostel.core.constants.EnumHelper.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpSendRequestVo {

    /** Email or mobile number; the code goes to that same channel. */
    @NotBlank(message = "identifier is required")
    @Size(max = 254, message = "identifier cannot exceed 254 characters")
    private String identifier;

    /** Defaults to LOGIN when omitted. */
    private OtpPurpose purpose;
}
