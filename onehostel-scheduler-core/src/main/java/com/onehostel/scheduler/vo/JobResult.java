package com.onehostel.scheduler.vo;

public record JobResult(int processed, int succeeded, int failed, String message) {

    public static JobResult empty(String message) {
        return new JobResult(0, 0, 0, message);
    }
}
