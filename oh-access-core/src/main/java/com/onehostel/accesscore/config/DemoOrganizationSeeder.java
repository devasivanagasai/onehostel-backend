package com.onehostel.accesscore.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.onehostel.accesscore.model.Organization;
import com.onehostel.accesscore.repository.OrganizationRepository;
import com.onehostel.common.constants.GlobalConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dev only (app.bootstrap.demo-organization): creates the organization that the demo owner and
 * tenant seeded by oh-auth-gtwy belong to, with the same fixed id. Never overwrites it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoOrganizationSeeder implements ApplicationRunner {

    private final CoreProperties properties;
    private final OrganizationRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.bootstrap().demoOrganization() || repository.existsById(GlobalConstants.DEMO_ORGANIZATION_ID)) {
            return;
        }
        Organization organization = new Organization("Sunrise Hostels");
        organization.setId(GlobalConstants.DEMO_ORGANIZATION_ID);
        repository.save(organization);
        log.info("Created demo organization {}", GlobalConstants.DEMO_ORGANIZATION_ID);
    }
}
