package com.onehostel.common.constants;

import java.time.Duration;

/**
 * One entry of a service's CacheSpec enum, the ONLY place a cache is declared:
 *
 * <pre>
 * public enum CacheSpec implements CacheSpecification {
 *     ROOM_TYPES(CacheNames.ROOM_TYPES, Duration.ofDays(7), 500);   // evicted by RoomTypeService on every change
 *     ...
 * }
 * </pre>
 *
 * Rules: every key includes organizationId; evict programmatically (AbstractCrudService.cacheNames / evictCache);
 * the TTL is a safety net, never the invalidation mechanism.
 */
public interface CacheSpecification {

    String cacheName();

    Duration ttl();

    long maxSize();
}
