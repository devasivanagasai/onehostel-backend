package com.onehostel.common.exception;

import com.onehostel.common.dto.ApiErrorResponse;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates persistence failures into catalogue codes. Separate from GlobalExceptionHandler because
 * the gateway has no data-access classes; registered only in services with MongoDB, and ordered
 * before GlobalExceptionHandler so its catch-all does not win.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataAccessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataAccessExceptionHandler.class);

    private final String sourceService;

    public DataAccessExceptionHandler(String sourceService) {
        this.sourceService = sourceService;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicateKeyException e) {
        log.warn("DUPLICATE_RECORD {}", e.getMostSpecificCause().getMessage());
        return respond(CommonErrorCode.DUPLICATE_RECORD, CommonErrorCode.DUPLICATE_RECORD.raise("Record").getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("OPTIMISTIC_LOCK_FAILED {}", e.getMessage());
        return respond(CommonErrorCode.OPTIMISTIC_LOCK_FAILED, CommonErrorCode.OPTIMISTIC_LOCK_FAILED.message());
    }

    private ResponseEntity<ApiErrorResponse> respond(ErrorCodeSpec code, String message) {
        return ResponseEntity.status(code.status()).body(ApiErrorResponse.of(code, message, List.of(), sourceService));
    }
}
