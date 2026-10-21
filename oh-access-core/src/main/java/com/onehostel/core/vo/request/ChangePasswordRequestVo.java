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
public class ChangePasswordRequestVo {

    @NotBlank(message = "currentPassword is required")
    @Size(max = 128, message = "currentPassword must not exceed 128 characters")
    private String currentPassword;

    @NotBlank(message = "newPassword is required")
    @Pattern(regexp = GlobalConstants.PASSWORD_REGEX, message = GlobalConstants.PASSWORD_POLICY_MESSAGE)
    private String newPassword;

    @Override
    public String toString() {
        return "ChangePasswordRequestVo{currentPassword='[PROTECTED]', newPassword='[PROTECTED]'}";
    }
}
