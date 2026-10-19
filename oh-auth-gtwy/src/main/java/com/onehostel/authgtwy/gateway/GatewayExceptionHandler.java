package com.onehostel.authgtwy.gateway;

import java.net.ConnectException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import com.onehostel.common.dto.CommonResponseObject;
import com.onehostel.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * A downstream service that is down or not answering becomes the standard envelope with 503
 * UPSTREAM_UNAVAILABLE, instead of a 500. Runs before the common GlobalExceptionHandler.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler {

    @ExceptionHandler({ ResourceAccessException.class, ConnectException.class })
    public ResponseEntity<CommonResponseObject<Void>> handleUpstreamUnavailable(Exception ex) {
        log.error("Downstream service unavailable: {}", ex.getMessage());
        ErrorCode code = ErrorCode.UPSTREAM_UNAVAILABLE;
        return ResponseEntity.status(code.status())
                .body(CommonResponseObject.error(code, "The service is temporarily unavailable. Please try again.", null));
    }
}
