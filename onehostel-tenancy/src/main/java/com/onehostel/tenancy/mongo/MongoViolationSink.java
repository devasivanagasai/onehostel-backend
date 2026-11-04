package com.onehostel.tenancy.mongo;

import java.time.Instant;
import java.util.Arrays;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.lang.Nullable;
import org.springframework.util.function.SingletonSupplier;

/**
 * Writes violations to tenancy_violations through a plain template on the unwrapped factory,
 * so recording a violation can never itself be scoped or recorded.
 */
public class MongoViolationSink implements ViolationSink {

    public static final String COLLECTION = "tenancy_violations";

    private static final Logger log = LoggerFactory.getLogger(MongoViolationSink.class);

    private final Supplier<MongoOperations> rawOperations;

    /** @param rawOperations resolved on first use, after the application context is up */
    public MongoViolationSink(Supplier<MongoOperations> rawOperations) {
        this.rawOperations = SingletonSupplier.of(rawOperations);
    }

    @Override
    public void record(String collectionName, String operation, @Nullable String tenantId, String reason) {
        String origin = callerOutsideFrameworks();
        log.warn("Tenancy violation (SHADOW): {} on {} tenant={} reason={} at {}", operation, collectionName, tenantId, reason, origin);
        try {
            rawOperations.get().insert(
                    new TenancyViolation(null, collectionName, operation, tenantId, reason, origin, Instant.now()));
        } catch (RuntimeException e) {
            // Best-effort: the violation is already in the log, and SHADOW must never break the request
            log.warn("Could not store tenancy violation for {}", collectionName, e);
        }
    }

    /** First stack frame in OneHostel code outside this library: where the unscoped access came from. */
    private static String callerOutsideFrameworks() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .map(StackTraceElement::toString)
                .filter(frame -> frame.startsWith("com.onehostel") && !frame.startsWith("com.onehostel.tenancy"))
                .findFirst()
                .orElse("unknown");
    }
}
