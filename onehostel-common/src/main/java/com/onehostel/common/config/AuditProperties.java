package com.onehostel.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.audit.* — retentionDays: how long audit entries are kept (TTL index on occurredAt). */
@ConfigurationProperties("app.audit")
public record AuditProperties(@DefaultValue("365") int retentionDays) {
}
