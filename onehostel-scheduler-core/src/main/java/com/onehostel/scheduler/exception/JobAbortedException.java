package com.onehostel.scheduler.exception;

/** Thrown by JobContext when a run crosses its abort rule; the run ends ABORTED. Jobs must not catch it. */
public class JobAbortedException extends RuntimeException {

    public JobAbortedException(String message) {
        super(message);
    }
}
