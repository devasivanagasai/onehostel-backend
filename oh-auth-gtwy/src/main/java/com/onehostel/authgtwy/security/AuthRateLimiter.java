package com.onehostel.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed one-minute window counter per key (client IP). In memory, so limits are per server
 * instance; move to Redis when the API runs on more than one instance.
 */
public class AuthRateLimiter {

    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long start, int count) {
    }

    private final int limit;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimiter(int limitPerMinute) {
        this.limit = limitPerMinute;
    }

    /** Seconds until the key may try again: 0 when this request is allowed. */
    public long tryAcquire(String key, Instant now) {
        long nowMillis = now.toEpochMilli();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> nowMillis - window.start() >= WINDOW_MILLIS);
        }
        Window window = windows.compute(key, (k, current) -> current == null || nowMillis - current.start() >= WINDOW_MILLIS
                ? new Window(nowMillis, 1)
                : new Window(current.start(), current.count() + 1));
        if (window.count() <= limit) {
            return 0;
        }
        return Math.max(1, (window.start() + WINDOW_MILLIS - nowMillis + 999) / 1000);
    }
}
