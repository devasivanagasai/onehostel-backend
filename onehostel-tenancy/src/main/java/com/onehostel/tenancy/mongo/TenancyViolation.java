package com.onehostel.tenancy.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** One recorded SHADOW-mode violation; kept for 90 days. */
@Document(MongoViolationSink.COLLECTION)
public record TenancyViolation(
        @Id String id,
        String collectionName,
        String operation,
        String tenantId,
        String reason,
        String origin,
        @Indexed(expireAfter = "90d") Instant recordedAt) {
}
