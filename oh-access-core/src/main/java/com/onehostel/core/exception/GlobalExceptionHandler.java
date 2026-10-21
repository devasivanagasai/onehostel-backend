package com.onehostel.core.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.onehostel.core.constants.ServiceConstants;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OneHostelException.class)
    public ResponseEntity<ApiErrorResponse> handleOneHostelException(OneHostelException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = HttpStatus.resolve(errorCode.getHttpStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (status.is5xxServerError()) {
            log.error("{} [{}] details={}", errorCode.getCode(), exception.getMessage(),
                    exception.getDetails(), exception);
        } else {
            log.warn("{} [{}] details={}", errorCode.getCode(), exception.getMessage(),
                    exception.getDetails());
        }
        return errorResponse(errorCode, exception.getMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return errorResponse(ErrorCode.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = exception.getReason() != null ? exception.getReason() : exception.getMessage();
        log.warn("Rejected request: status={} message={}", status, message);
        return respond(status.name(), message, status, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ApiFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("Validation failed on {}: {} field(s)", exception.getObjectName(), fields.size());
        return errorResponse(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiFieldError> fields = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : null,
                        violation.getMessage()))
                .toList();
        log.warn("Constraint violation: {} field(s)", fields.size());
        return errorResponse(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), fields);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(ValidationException exception) {
        log.warn("Validation rejected: {}", exception.getMessage());
        return errorResponse(ErrorCode.VALIDATION_FAILED, exception.getMessage(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Malformed request body: {}", exception.getMostSpecificCause().getMessage());
        return errorResponse(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.getDefaultMessage(), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception) {
        log.warn("Missing request parameter: {}", exception.getParameterName());
        return errorResponse(ErrorCode.BAD_REQUEST, "Missing required parameter: " + exception.getParameterName(), null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception) {
        log.warn("Optimistic lock lost: {}", exception.getMessage());
        return errorResponse(ErrorCode.BAD_REQUEST, "Record was updated concurrently. Please retry.", null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey(DuplicateKeyException exception) {
        log.warn("Duplicate key rejected: {}", exception.getMostSpecificCause().getMessage());
        return errorResponse(ErrorCode.DUPLICATE_RECORD, "Record already exists.", null);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstreamResponse(WebClientResponseException exception) {
        HttpRequest request = exception.getRequest();
        log.error("UPSTREAM_ERROR upstreamStatus={} path={} upstreamBody={} [correlationId={}]",
                exception.getStatusCode().value(),
                request != null ? request.getMethod() + " " + request.getURI() : "unknown",
                exception.getResponseBodyAsString(),
                getCorrelationId());
        return errorResponse(ErrorCode.INTERNAL_ERROR,
                "A system we depend on rejected the request. Please try again later.", null);
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstreamUnreachable(WebClientRequestException exception) {
        log.error("UPSTREAM_UNAVAILABLE path={} {} cause={} [correlationId={}]",
                exception.getMethod(), exception.getUri(),
                exception.getMostSpecificCause().getMessage(),
                getCorrelationId());
        return errorResponse(ErrorCode.INTERNAL_ERROR,
                "A system we depend on is unavailable. Please try again.", null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
        log.error("Unclassified IllegalStateException reached GlobalExceptionHandler [correlationId={}]",
                getCorrelationId(), exception);
        return errorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception reached GlobalExceptionHandler [correlationId={}]",
                getCorrelationId(), exception);
        return errorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null);
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(ErrorCode errorCode, String overrideMessage,
                                                           List<ApiFieldError> fieldErrors) {
        String message = overrideMessage != null && !overrideMessage.isBlank()
                ? overrideMessage
                : errorCode.getDefaultMessage();
        HttpStatus status = HttpStatus.resolve(errorCode.getHttpStatus());
        return respond(errorCode.getCode(), message, status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, fieldErrors);
    }

    private ResponseEntity<ApiErrorResponse> respond(String errorCode, String message, HttpStatus status,
                                                     List<ApiFieldError> fieldErrors) {
        return respond(errorCode, message, status, fieldErrors, "oh-access-core");
    }

    private ResponseEntity<ApiErrorResponse> respond(String errorCode, String message, HttpStatus status,
                                                     List<ApiFieldError> fieldErrors, String sourceService) {
        ApiErrorResponse response = new ApiErrorResponse(
                errorCode,
                message,
                status.value(),
                Instant.now(),
                getCorrelationId(),
                sourceService,
                fieldErrors != null && !fieldErrors.isEmpty() ? fieldErrors : null);

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    private String getCorrelationId() {
        String correlationId = MDC.get(ServiceConstants.MDC_CORRELATION_ID_KEY);
        return correlationId != null ? correlationId : MDC.get("correlationId");
    }
}