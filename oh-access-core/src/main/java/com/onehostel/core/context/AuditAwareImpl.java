package com.onehostel.core.context;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;

public class AuditAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_USER = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        String userId = UserContextHolder.getUserId();
        return Optional.ofNullable(userId != null && !userId.isBlank() ? userId : SYSTEM_USER);
    }
}