package com.onehostel.dto.auth;

/**
 * Same for every login flow and for refresh. Send {@code accessToken} as
 * {@code Authorization: Bearer <accessToken>}; renew it through /refresh-token before
 * {@code expiresIn} seconds pass. The refresh token itself is only in the HttpOnly cookie.
 */
public record LoginResponse(String tokenType, String accessToken, long expiresIn, UserProfile userProfile) {

    public static LoginResponse bearer(String accessToken, long expiresIn, UserProfile userProfile) {
        return new LoginResponse("Bearer", accessToken, expiresIn, userProfile);
    }
}
