package com.onehostel.access.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.onehostel.access.client.KeycloakAdminClient;
import com.onehostel.access.constants.AccessEnums.EntityType;
import com.onehostel.access.dto.ProfileDtos.ProfileResponse;
import com.onehostel.access.dto.ProfileDtos.ProfileUpdateRequest;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.model.User;
import com.onehostel.access.repository.UserRepository;
import com.onehostel.access.vo.MobileNumber;
import com.onehostel.common.constants.CommonEnums.AuditAction;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.service.AuditRecorder;

import lombok.RequiredArgsConstructor;

/**
 * The signed-in user's own profile. Name and email live in two places: the users collection (lookups at
 * sign-in, the app) and Keycloak (the name and email claims of every token), so an update writes both.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository users;
    private final KeycloakAdminClient keycloakAdmin;
    private final AuditRecorder audit;

    public ProfileResponse get(String userId) {
        return toResponse(require(userId));
    }

    public ProfileResponse update(String userId, ProfileUpdateRequest request) {
        User user = require(userId);
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        MobileNumber mobile = new MobileNumber(request.mobileCountryCode().trim(), request.mobileNumber().trim());

        users.findByEmailAndDeletedFalse(email).filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw ErrorCode.EMAIL_IN_USE.raise();
                });
        users.findByMobileNumber(mobile.number()).filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw ErrorCode.MOBILE_IN_USE.raise();
                });

        List<String> changed = new ArrayList<>();
        if (!Objects.equals(user.getFirstName(), firstName)) changed.add("firstName");
        if (!Objects.equals(user.getLastName(), lastName)) changed.add("lastName");
        if (!Objects.equals(user.getEmail(), email)) changed.add("email");
        if (!Objects.equals(user.getMobileNumber(), mobile)) changed.add("mobileNumber");
        if (changed.isEmpty()) {
            return toResponse(user);
        }

        // Keycloak first: it is the call more likely to fail (validation, availability), and if the save
        // below then fails, Keycloak only shows the new name/email in tokens until the user saves again
        if (changed.contains("firstName") || changed.contains("lastName") || changed.contains("email")) {
            keycloakAdmin.updateProfile(
                    keycloakAdmin.requireUserId(user.getKeycloakUserId(), user.keycloakUsername()), firstName, lastName, email);
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setMobileNumber(mobile);
        User saved = users.save(user);
        audit.record(AuditAction.UPDATE, EntityType.USER.name(), saved.getId(), saved.getOrganizationId(),
                "Profile updated: " + String.join(", ", changed));
        return toResponse(saved);
    }

    private User require(String userId) {
        return (userId == null ? Optional.<User>empty() : users.findById(userId))
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> CommonErrorCode.ENTITY_NOT_FOUND.raise("User", userId));
    }

    private static ProfileResponse toResponse(User user) {
        MobileNumber mobile = user.getMobileNumber();
        return new ProfileResponse(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(), user.getEmail(),
                mobile != null ? mobile.countryCode() : null, mobile != null ? mobile.number() : null,
                user.primaryRole(), user.getCreatedAt(), user.getPasswordChangedAt());
    }
}
