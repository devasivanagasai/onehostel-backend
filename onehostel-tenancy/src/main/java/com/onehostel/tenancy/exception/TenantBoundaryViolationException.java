package com.onehostel.tenancy.exception;

/**
 * A data access that would cross the tenant boundary, or one made without a tenant.
 * GlobalExceptionHandler in onehostel-common maps it to 403 SECURITY_BREACH.
 */
public class TenantBoundaryViolationException extends RuntimeException {

    public TenantBoundaryViolationException(String message) {
        super(message);
    }
}
