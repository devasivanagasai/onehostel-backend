package com.onehostel.core.vo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponseVo {

    @Builder.Default
    private String tokenType = "Bearer";

    private String accessToken;

    private long expiresIn;

    private UserProfileVo userProfile;
}