package com.onehostel.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.onehostel.security.AuthenticatedUser;

/**
 * Fills {@code @CreatedDate} / {@code @LastModifiedDate} fields on save, and
 * {@code @CreatedBy} / {@code @LastModifiedBy} with the signed-in user's id.
 */
@Configuration
@EnableMongoAuditing(auditorAwareRef = "auditorAware")
public class MongoConfig {

    /** Empty outside a request (startup seeding, scheduled jobs): those writes have no user. */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                return Optional.of(user.id());
            }
            return Optional.empty();
        };
    }
}
