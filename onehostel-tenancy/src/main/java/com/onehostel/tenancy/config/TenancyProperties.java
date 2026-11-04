package com.onehostel.tenancy.config;

import com.onehostel.tenancy.constants.TenancyEnums.TenancyMode;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.tenancy.*
 *
 * @param mode            global mode; ENFORCE from the first commit
 * @param collectionModes per-collection override, e.g. app.tenancy.collection-modes.rooms=SHADOW
 */
@ConfigurationProperties("app.tenancy")
public record TenancyProperties(
        @DefaultValue("ENFORCE") TenancyMode mode,
        Map<String, TenancyMode> collectionModes) {

    public TenancyProperties {
        collectionModes = collectionModes == null ? Map.of() : Map.copyOf(collectionModes);
    }

    public TenancyMode modeFor(String collectionName) {
        return collectionModes.getOrDefault(collectionName, mode);
    }
}
