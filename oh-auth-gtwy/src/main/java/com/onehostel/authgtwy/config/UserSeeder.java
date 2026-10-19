package com.onehostel.config;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.GlobalConstants;
import com.onehostel.model.User;
import com.onehostel.repository.UserRepository;
import com.onehostel.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates accounts at startup so there is someone to log in as. There is no sign-up endpoint
 * yet: until onboarding exists, this is the only way accounts are created.
 *
 * - Super admin (any profile): ADMIN_EMAIL / ADMIN_PASSWORD, optional ADMIN_USERNAME and ADMIN_PASSCODE.
 *   The dev profile has built-in defaults for these (application-dev.yml).
 * - Demo hostel owner and tenant (dev only, app.bootstrap.demo-users=true).
 *
 * An existing account is never overwritten; only fields it is missing are filled in, so
 * accounts seeded before a field existed (organization, phone, passcode) gain it.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class UserSeeder implements ApplicationRunner {

    /** Dev-only demo passwords, publicly known. */
    static final String DEMO_OWNER_PASSWORD = "Owner@OneHostel2026";
    static final String DEMO_TENANT_PASSWORD = "Tenant@OneHostel2026";

    /** The owner's property group; the tenant lives in it, so the tenant can see the owner's hostels. */
    static final String DEMO_ORGANIZATION_ID = "org-sunrise-hostels";

    private record Seed(String name, String email, String username, String phoneNumber, String password,
            String passcode, Role role, String organizationId, String roomNumber, String bedId) {
    }

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = properties.bootstrap();
        if (bootstrap == null) {
            return;
        }
        if (bootstrap.enabled()) {
            String passcode = bootstrap.adminPasscode();
            if (passcode != null && !passcode.isBlank() && !passcode.matches(GlobalConstants.PASSCODE_REGEX)) {
                log.warn("ADMIN_PASSCODE ignored: it must be exactly 6 digits");
                passcode = null;
            }
            String username = bootstrap.adminUsername() == null || bootstrap.adminUsername().isBlank()
                    ? null
                    : bootstrap.adminUsername().trim().toLowerCase(Locale.ROOT);
            createOrComplete(new Seed(bootstrap.adminName(), bootstrap.adminEmail(), username, null,
                    bootstrap.adminPassword(), passcode, Role.SUPER_ADMIN, null, null, null));
        }
        if (bootstrap.demoUsers()) {
            createOrComplete(new Seed("Rajesh Kumar", "rajesh.kumar@onehostel.com", null, "9848012345",
                    DEMO_OWNER_PASSWORD, null, Role.HOSTEL_OWNER, DEMO_ORGANIZATION_ID, null, null));
            createOrComplete(new Seed("Priya Sharma", "priya.sharma@onehostel.com", null, "9701234567",
                    DEMO_TENANT_PASSWORD, null, Role.HOSTEL_TENANT, DEMO_ORGANIZATION_ID, "101",
                    "ROOM-101-BED-A"));
        }
    }

    private void createOrComplete(Seed seed) {
        String email = AuthService.normalizeEmail(seed.email());
        User user = userRepository.findByEmail(email).orElse(null);
        // Seed data must never stop the application: a clash is logged and the account skipped.
        try {
            if (user == null) {
                user = new User(seed.name(), email, passwordEncoder.encode(seed.password()), seed.role());
                fillMissing(user, seed);
                userRepository.save(user);
                log.info("Created {} account {}", seed.role(), email);
            } else if (fillMissing(user, seed)) {
                userRepository.save(user);
                log.info("Completed missing fields on {}", email);
            }
        } catch (DuplicateKeyException ex) {
            log.warn("Could not seed {}: {}", email, ex.getMostSpecificCause().getMessage());
        }
    }

    private boolean fillMissing(User user, Seed seed) {
        boolean changed = false;
        if (user.getUsername() == null && seed.username() != null
                && isFree(userRepository.findByUsername(seed.username()), user, "username " + seed.username())) {
            user.setUsername(seed.username());
            changed = true;
        }
        if (user.getPhoneNumber() == null && seed.phoneNumber() != null
                && isFree(userRepository.findByPhoneNumber(seed.phoneNumber()), user, "mobile " + seed.phoneNumber())) {
            user.setPhoneNumber(seed.phoneNumber());
            changed = true;
        }
        if (user.getPasscodeHash() == null && seed.passcode() != null) {
            user.setPasscodeHash(passwordEncoder.encode(seed.passcode()));
            changed = true;
        }
        if (user.getOrganizationId() == null && seed.organizationId() != null) {
            user.setOrganizationId(seed.organizationId());
            changed = true;
        }
        if (user.getRoomNumber() == null && seed.roomNumber() != null) {
            user.setRoomNumber(seed.roomNumber());
            changed = true;
        }
        if (user.getBedId() == null && seed.bedId() != null) {
            user.setBedId(seed.bedId());
            changed = true;
        }
        return changed;
    }

    /** Usernames and mobiles are unique; one held by a different account is left off this one. */
    private static boolean isFree(Optional<User> holder, User user, String what) {
        if (holder.isEmpty() || Objects.equals(holder.get().getId(), user.getId())) {
            return true;
        }
        log.warn("{} already belongs to {} ({}); {} is seeded without it", what, holder.get().getEmail(),
                holder.get().getRole(), user.getEmail());
        return false;
    }
}
