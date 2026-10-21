package com.onehostel.gateway.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import com.onehostel.core.dto.CommonResponseObject;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.exception.OneHostelException;

import lombok.extern.slf4j.Slf4j;

/**
 * Reactive counterpart of access-core's servlet GlobalExceptionHandler,
 * so the frontend gets proper status codes and the common response shape.
 */
@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(OneHostelException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleOneHostel(OneHostelException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("{}: {}", code.getCode(), ex.getMessage());
        return build(code, ex.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleValidation(WebExchangeBindException ex) {
        List<CommonResponseObject.FieldError> fields = ex.getFieldErrors().stream()
                .map(fe -> new CommonResponseObject.FieldError(
                        fe.getField().replaceFirst("^requestObject\\.", ""), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(CommonResponseObject.error(ErrorCode.VALIDATION_FAILED, null, fields, null));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleBadInput(Exception ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(ErrorCode.MALFORMED_REQUEST, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        ErrorCode code = status.value() == 404 ? ErrorCode.ROUTE_NOT_FOUND
                : status.is4xxClientError() ? ErrorCode.BAD_REQUEST
                : ErrorCode.INTERNAL_ERROR;
        CommonResponseObject<Void> body = CommonResponseObject.error(code, null, null, null);
        body.setStatusCode(status.value());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseObject<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<CommonResponseObject<Void>> build(ErrorCode code, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(code.getHttpStatus()))
                .body(CommonResponseObject.error(code, message, null, null));
    }
}
