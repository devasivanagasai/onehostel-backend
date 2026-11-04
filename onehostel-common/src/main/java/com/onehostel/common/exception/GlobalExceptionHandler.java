package com.onehostel.common.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.onehostel.common.dto.ApiErrorResponse;
import com.onehostel.common.dto.ApiFieldError;
import com.onehostel.common.helper.CorrelationContext;
import com.onehostel.tenancy.exception.TenantBoundaryViolationException;

import jakarta.validation.ConstraintViolationException;

/**
 * The only place that turns an exception into a response. Services throw (ErrorCode.X.raise(...)),
 * controllers never catch. 4xx are logged at WARN without a stack; 5xx at ERROR with stack and context.
 * Internal detail (exception messages, stack traces) never reaches the client.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final String sourceService;

    public GlobalExceptionHandler(String sourceService) {
        this.sourceService = sourceService;
    }

    @ExceptionHandler(HostelException.class)
    public ResponseEntity<ApiErrorResponse> handleHostel(HostelException e) {
        if (e.getHttpStatus().is5xxServerError()) {
            log.error("{} {} context={}", e.getCode(), e.getMessage(), e.getContext(), e);
        } else {
            log.warn("{} {} context={}", e.getCode(), e.getMessage(), e.getContext());
        }
        return respond(e.getErrorCode(), e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        List<ApiFieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("VALIDATION_FAILED {}", errors);
        return respond(CommonErrorCode.VALIDATION_FAILED, CommonErrorCode.VALIDATION_FAILED.message(), errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidParameters(HandlerMethodValidationException e) {
        List<ApiFieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiFieldError(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        log.warn("VALIDATION_FAILED {}", errors);
        return respond(CommonErrorCode.VALIDATION_FAILED, CommonErrorCode.VALIDATION_FAILED.message(), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<ApiFieldError> errors = e.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        log.warn("VALIDATION_FAILED {}", errors);
        return respond(CommonErrorCode.VALIDATION_FAILED, CommonErrorCode.VALIDATION_FAILED.message(), errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("MALFORMED_REQUEST {}", e.getMostSpecificCause().getMessage());
        return respond(CommonErrorCode.MALFORMED_REQUEST, CommonErrorCode.MALFORMED_REQUEST.message(), List.of());
    }

    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleBadParameter(Exception e) {
        log.warn("BAD_REQUEST {}", e.getMessage());
        String message = e instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value for " + mismatch.getName()
                : e.getMessage();
        return respond(CommonErrorCode.BAD_REQUEST, message, List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNoRoute(Exception e) {
        log.warn("ROUTE_NOT_FOUND {}", e.getMessage());
        return respond(CommonErrorCode.ROUTE_NOT_FOUND, CommonErrorCode.ROUTE_NOT_FOUND.message(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.warn("METHOD_NOT_ALLOWED {}", e.getMethod());
        return respond(CommonErrorCode.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.raise(e.getMethod()).getMessage(), List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        String contentType = String.valueOf(e.getContentType());
        log.warn("UNSUPPORTED_MEDIA_TYPE {}", contentType);
        return respond(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
                CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.raise(contentType).getMessage(), List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        String code = status instanceof HttpStatus known ? known.name() : "HTTP_" + status.value();
        String message = e.getReason() != null ? e.getReason() : code;
        log.warn("{} {}", code, message);
        return ResponseEntity.status(status).body(new ApiErrorResponse(false, code, message, status.value(), List.of(),
                CorrelationContext.current(), sourceService, Instant.now()));
    }

    @ExceptionHandler(TenantBoundaryViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantBoundary(TenantBoundaryViolationException e) {
        log.error("SECURITY_BREACH tenant boundary: {}", e.getMessage());
        return respond(CommonErrorCode.SECURITY_BREACH, CommonErrorCode.SECURITY_BREACH.message(), List.of());
    }

    /** An upstream (another service, Keycloak) could not be reached. */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstreamDown(ResourceAccessException e) {
        log.error("SERVICE_UNAVAILABLE {}", e.getMessage());
        return respond(CommonErrorCode.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE.message(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("INTERNAL_ERROR unhandled {}", e.getClass().getName(), e);
        return respond(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.message(), List.of());
    }

    private ResponseEntity<ApiErrorResponse> respond(ErrorCodeSpec code, String message, List<ApiFieldError> errors) {
        return ResponseEntity.status(code.status()).body(ApiErrorResponse.of(code, message, errors, sourceService));
    }
}
