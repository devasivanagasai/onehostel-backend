package com.onehostel.tenancy.mongo;

import org.springframework.lang.Nullable;

/** Where SHADOW-mode boundary violations go. Recording must never fail the caller's operation. */
public interface ViolationSink {

    void record(String collectionName, String operation, @Nullable String tenantId, String reason);
}
