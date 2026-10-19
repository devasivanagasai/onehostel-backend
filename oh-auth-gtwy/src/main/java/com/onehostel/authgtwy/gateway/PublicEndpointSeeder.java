package com.onehostel.authgtwy.gateway;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.onehostel.authgtwy.config.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * At startup: stores any missing built-in public endpoint (so the full list is visible and
 * manageable in the database), then loads the stored list into PublicEndpointRegistry.
 * Disabled with app.gateway.seed-public-endpoints=false (tests), leaving the built-in list.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class PublicEndpointSeeder implements ApplicationRunner {

    private final AppProperties properties;
    private final PublicEndpointRepository repository;
    private final PublicEndpointRegistry registry;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.gateway().seedPublicEndpoints()) {
            return;
        }
        for (PublicEndpoint builtIn : PublicEndpointRegistry.BUILT_IN) {
            if (repository.findByPatternAndMethod(builtIn.getPattern(), builtIn.getMethod()).isEmpty()) {
                repository.save(new PublicEndpoint(builtIn.getPattern(), builtIn.getMethod(),
                        builtIn.getDescription(), true));
                log.info("Seeded public endpoint {} {}", builtIn.getMethod(), builtIn.getPattern());
            }
        }
        registry.refresh();
    }
}
