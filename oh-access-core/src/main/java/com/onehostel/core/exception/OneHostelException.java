package com.onehostel.core.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public class OneHostelException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details = new HashMap<>();

    public OneHostelException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public OneHostelException(ErrorCode errorCode, String message) {
        super(message != null ? message : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public OneHostelException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public OneHostelException(ErrorCode errorCode, String message, Throwable cause) {
        super(message != null ? message : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * Fluent helper to attach debug/audit metadata (logged securely, never exposed raw to clients).
     */
    public OneHostelException with(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }
}