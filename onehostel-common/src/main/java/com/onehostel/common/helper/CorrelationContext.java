package com.onehostel.common.helper;

import org.slf4j.MDC;

/** The request's correlation id, kept in the logging MDC and propagated on every outbound hop. */
public final class CorrelationContext {

    public static final String HEADER = "X-Correlation-Id";
    /** MDC key for the log pattern: %X{correlationId}. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationContext() {
    }

    /** The current correlation id, or null outside a request / decorated task. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
