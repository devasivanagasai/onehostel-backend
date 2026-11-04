package com.onehostel.common.exception;

import java.text.MessageFormat;

import org.springframework.http.HttpStatus;

/**
 * An error's code, HTTP status and message template, declared together in one place
 * (CommonErrorCode, and each service's ErrorCode enum). The only way to create a HostelException:
 * {@code throw ErrorCode.BED_ALREADY_OCCUPIED.raise(bedNo);}
 */
public interface ErrorCodeSpec {

    /** Stable API value: never rename; add a new code and deprecate the old one. */
    String code();

    HttpStatus status();

    /** MessageFormat template, e.g. "{0} not found: {1}". Write "''" for an apostrophe. */
    String message();

    default HostelException raise(Object... args) {
        return new HostelException(this, format(args), null);
    }

    /** Separate name on purpose: a raise(Throwable, Object...) overload would silently bind a lone cause as a message arg. */
    default HostelException raiseCausedBy(Throwable cause, Object... args) {
        return new HostelException(this, format(args), cause);
    }

    /** For callers that compose the whole sentence. Prefer a new catalogue entry if the sentence recurs. */
    default HostelException raiseWithMessage(String message) {
        return new HostelException(this, message, null);
    }

    private String format(Object... args) {
        return (args == null || args.length == 0) ? message() : MessageFormat.format(message(), args);
    }
}
