package com.onehostel.common.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.crud.*
 *
 * @param defaultPageSize page size when the request gives none
 * @param maxPageSize     hard cap on any requested page size
 * @param zone            zone for DATE filters ("2026-01-01" means that day here)
 */
@ConfigurationProperties("app.crud")
public record CrudProperties(
        @DefaultValue("20") int defaultPageSize,
        @DefaultValue("200") int maxPageSize,
        @DefaultValue("Asia/Kolkata") ZoneId zone) {
}
