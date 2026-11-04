package com.onehostel.access.vo;

import com.onehostel.common.advice.RequestEnvelope;
import com.onehostel.common.constants.CommonEnums.ClientSource;
import com.onehostel.common.vo.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Where a sign-in request comes from, recorded with the refresh token.
 *
 * @param ipAddress the client's address as the gateway saw it (the socket address when called directly)
 */
public record ClientInfo(ClientSource clientSource, String deviceId, String ipAddress, String userAgent) {

    public static ClientInfo of(HttpServletRequest request, String deviceId) {
        String gatewayClientIp = CurrentUser.from(request).clientIp();
        return new ClientInfo(
                RequestEnvelope.clientSource().orElse(null),
                deviceId,
                gatewayClientIp != null ? gatewayClientIp : request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }
}
