package com.onehostel.common.helper;

import com.onehostel.common.constants.CacheSpecification;

import java.util.Arrays;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Builds the service's CacheManager from its CacheSpec values. SimpleCacheManager knows only the declared
 * caches, so a misspelt cache name fails loudly instead of silently not caching.
 * Caffeine is per pod: past one replica, add cross-pod eviction or move to Redis.
 */
public final class CaffeineCaches {

    private CaffeineCaches() {
    }

    public static CacheManager manager(CacheSpecification... specs) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.stream(specs)
                .map(spec -> new CaffeineCache(spec.cacheName(), Caffeine.newBuilder()
                        .expireAfterWrite(spec.ttl())
                        .maximumSize(spec.maxSize())
                        .recordStats()
                        .build()))
                .toList());
        manager.initializeCaches();
        return manager;
    }
}
