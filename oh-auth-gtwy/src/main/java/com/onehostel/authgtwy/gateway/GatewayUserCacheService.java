package com.onehostel.authgtwy.gateway;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.onehostel.authgtwy.config.AppProperties;
import com.onehostel.authgtwy.model.User;
import com.onehostel.authgtwy.repository.UserRepository;

/**
 * Whether a token's user may still use the platform, cached per user (Caffeine). Access tokens
 * stay cryptographically valid until they expire; this check lets the gateway refuse a disabled,
 * locked or deleted account within app.gateway.user-cache-ttl, without a database read per request.
 */
@Service
public class GatewayUserCacheService {

    private final UserRepository userRepository;
    private final Cache<String, Boolean> active;

    public GatewayUserCacheService(UserRepository userRepository, AppProperties properties) {
        this.userRepository = userRepository;
        this.active = Caffeine.newBuilder()
                .expireAfterWrite(properties.gateway().userCacheTtl())
                .maximumSize(10_000)
                .build();
    }

    public boolean isActive(String userId) {
        return active.get(userId, id -> userRepository.findById(id).map(User::canLogIn).orElse(false));
    }

    /** Call after changing an account's state, so the next request sees it at once. */
    public void evict(String userId) {
        active.invalidate(userId);
    }
}
