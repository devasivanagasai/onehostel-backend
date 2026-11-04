package com.onehostel.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** The application exception. Created only through {@link ErrorCodeSpec#raise}; mapped by GlobalExceptionHandler. */
@Getter
public class HostelException extends RuntimeException {

    private final ErrorCodeSpec errorCode;
    private final transient Map<String, Object> context = new LinkedHashMap<>();

    // Package-private: raise() is the ONLY way in
    HostelException(ErrorCodeSpec errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Diagnostic value: logged, NEVER serialised to the client. */
    public HostelException with(String key, Object value) {
        context.put(key, value);
        return this;
    }

    public Map<String, Object> getContext() {
        return Collections.unmodifiableMap(context);
    }

    public HttpStatus getHttpStatus() {
        return errorCode.status();
    }

    public String getCode() {
        return errorCode.code();
    }
}
