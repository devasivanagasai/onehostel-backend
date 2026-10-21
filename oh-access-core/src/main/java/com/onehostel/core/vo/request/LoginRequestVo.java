package com.onehostel.core.vo.request;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class LoginRequestVo {

    /** Email, mobile number or username. "userName" and "email" are accepted as older field names. */
    @NotBlank(message = "identifier is required")
    @Size(max = 254, message = "identifier must not exceed 254 characters")
    @JsonAlias({"userName", "email"})
    private String identifier;

    @NotBlank(message = "password is required")
    @Size(max = 128, message = "password must not exceed 128 characters")
    private String password;

    @Override
    public String toString() {
        return "LoginRequestVo{identifier='" + identifier + "', password='[PROTECTED]'}";
    }
}
