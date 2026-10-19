package com.onehostel.exception;

public class OneHostelException extends RuntimeException {

    private final transient ErrorCode errorCode;

    OneHostelException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
