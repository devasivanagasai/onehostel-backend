package com.onehostel.access.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.onehostel.access.service.PrivilegeService;

import lombok.RequiredArgsConstructor;

/** Syncs PrivilegeCatalog into the database at every startup (idempotent). */
@Component
@RequiredArgsConstructor
public class PrivilegeSeedRunner implements ApplicationRunner {

    private final PrivilegeService privilegeService;

    @Override
    public void run(ApplicationArguments args) {
        privilegeService.syncCatalog();
    }
}
