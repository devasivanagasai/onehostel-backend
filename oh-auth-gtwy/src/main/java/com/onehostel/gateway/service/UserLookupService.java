package com.onehostel.gateway.service;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.onehostel.core.constants.EnumHelper.ClientSource;
import com.onehostel.core.constants.EnumHelper.Role;
import com.onehostel.core.constants.EnumHelper.UserStatus;
import com.onehostel.core.constants.GlobalConstants;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.model.UserDoc;
import com.onehostel.core.util.LoginIdentifier;
import com.onehostel.gateway.repository.ReactiveUserRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/** Resolves a login identifier (email, mobile number or username) to a user and checks they may sign in. */
@Service
@RequiredArgsConstructor
public class UserLookupService {

    /** Roles that may only sign in through the mobile app, not the web portal. */
    private static final Set<Role> MOBILE_ONLY_ROLES = Set.of(Role.HOSTEL_TENANT, Role.SECURITY_GUARD);

    private final ReactiveUserRepository userRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    /** Lower-cases emails/usernames and reduces phone numbers like "+91 90000 00001" to 10 digits. */
    public static String normalize(String raw) {
        String trimmed = raw.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase();
        }
        String digits = trimmed.replaceAll("[\\s()+-]", "");
        if (digits.matches("\\d{11,13}")) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits.matches(GlobalConstants.PHONE_REGEX) ? digits : trimmed.toLowerCase();
    }

    /** Looks up an already normalized identifier; empty when no active account matches. */
    public Mono<UserDoc> findByIdentifier(String normalizedIdentifier) {
        LoginIdentifier parsed = LoginIdentifier.parse(normalizedIdentifier);
        return switch (parsed.type()) {
            case EMAIL -> userRepository.findByEmailAndIsDeletedFalse(parsed.value());
            case PHONE -> userRepository.findFirstByMobileNumber_NumberAndIsDeletedFalse(parsed.value());
            case USERNAME -> userRepository.findByUserNameAndIsDeletedFalse(parsed.value());
        };
    }

    /** Rejects locked, disabled or blocked accounts. */
    public Mono<UserDoc> ensureCanSignIn(UserDoc user) {
        if (user.isLocked()) {
            return Mono.error(ErrorCode.ACCOUNT_LOCKED.raise());
        }
        if (user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.BLOCKED) {
            return Mono.error(ErrorCode.ACCOUNT_DISABLED.raise());
        }
        return Mono.just(user);
    }

    /** Tenants and security guards must use the mobile app; the web portal is for staff and admins. */
    public Mono<UserDoc> ensureAllowedOnClient(UserDoc user, ClientSource clientSource) {
        Role role = user.getPrimaryAccess() != null ? user.getPrimaryAccess().getRole() : null;
        if (clientSource == ClientSource.WEB && role != null && MOBILE_ONLY_ROLES.contains(role)) {
            return Mono.error(ErrorCode.MOBILE_APP_ONLY.raise());
        }
        return Mono.just(user);
    }

    /** Account behind an access token: by Keycloak id (the "sub" claim), else by username. */
    public Mono<UserDoc> findByKeycloakIdOrUsername(String keycloakUserId, String username) {
        Mono<UserDoc> byUsername = username == null ? Mono.empty()
                : Mono.defer(() -> findByIdentifier(normalize(username)));
        return userRepository.findByKeycloakUserIdAndIsDeletedFalse(keycloakUserId).switchIfEmpty(byUsername);
    }

    /**
     * Stamps passwordChangedAt with a field-level update. A full save() would treat seeded
     * records without a version field as new documents and fail on the duplicate _id.
     */
    public Mono<Instant> markPasswordChanged(String userId) {
        Instant now = Instant.now();
        Update update = new Update().set("passwordChangedAt", now).set("updatedAt", now);
        return mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(userId)), update, UserDoc.class)
                .thenReturn(now);
    }

    /** The username Keycloak knows the user by (accounts are created with the username, else the email). */
    public static String keycloakUsername(UserDoc user) {
        return user.getUserName() != null ? user.getUserName() : user.getEmail();
    }
}
