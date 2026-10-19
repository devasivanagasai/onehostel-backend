package com.onehostel.exception;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.onehostel.dto.helper.CommonResponseObject;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OneHostelException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleOneHostel(OneHostelException ex) {
        ErrorCode code = ex.getErrorCode();
        if (code.status().is5xxServerError()) {
            log.error("{}: {}", code.code(), ex.getMessage(), ex);
        } else {
            log.info("{}: {}", code.code(), ex.getMessage());
        }
        return build(code, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<CommonResponseObject.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new CommonResponseObject.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.format(), fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(ErrorCode.MISSING_REQUIRED_PARAMETER,
                ErrorCode.MISSING_REQUIRED_PARAMETER.format(ex.getParameterName()), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleNotFound(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.info("Duplicate key: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.DUPLICATE_RECORD, ErrorCode.DUPLICATE_RECORD.format("This record"), null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<CommonResponseObject<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(ErrorCode.OPTIMISTIC_LOCK_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseObject<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<CommonResponseObject<Void>> build(ErrorCode code) {
        return build(code, code.format(), null);
    }

    private static ResponseEntity<CommonResponseObject<Void>> build(ErrorCode code, String message,
            List<CommonResponseObject.FieldError> fieldErrors) {
        return ResponseEntity.status(code.status()).body(CommonResponseObject.error(code, message, fieldErrors));
    }
}
