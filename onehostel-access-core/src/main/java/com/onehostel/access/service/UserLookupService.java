package com.onehostel.access.service;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.constants.AccessEnums.UserStatus;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.model.User;
import com.onehostel.access.repository.UserRepository;
import com.onehostel.common.constants.CommonEnums.ClientSource;

import lombok.RequiredArgsConstructor;

/** Resolves a login identifier (email, mobile number or username) to a user and checks they may sign in. */
@Service
@RequiredArgsConstructor
public class UserLookupService {

    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern PHONE_WITH_PREFIX = Pattern.compile("^\\d{11,13}$");

    private final UserRepository userRepository;
    private final MongoOperations mongo;

    /** Lower-cases emails and usernames and reduces "+91 98480-12345" to its 10 digits. */
    public static String normalize(String raw) {
        String trimmed = raw.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase();
        }
        String digits = trimmed.replaceAll("[\\s()+-]", "");
        if (PHONE_WITH_PREFIX.matcher(digits).matches()) {
            digits = digits.substring(digits.length() - 10);
        }
        return PHONE.matcher(digits).matches() ? digits : trimmed.toLowerCase();
    }

    public static boolean isPhone(String normalizedIdentifier) {
        return PHONE.matcher(normalizedIdentifier).matches();
    }

    /** Looks up an already normalized identifier; empty when no live account matches. */
    public Optional<User> findByIdentifier(String normalizedIdentifier) {
        if (normalizedIdentifier.contains("@")) {
            return userRepository.findByEmailAndDeletedFalse(normalizedIdentifier);
        }
        if (isPhone(normalizedIdentifier)) {
            return userRepository.findByMobileNumber(normalizedIdentifier);
        }
        return userRepository.findByUsernameAndDeletedFalse(normalizedIdentifier);
    }

    /** The account behind an access token: by Keycloak id (sub), else by username. */
    public Optional<User> findByKeycloakIdOrUsername(String keycloakUserId, String username) {
        Optional<User> byKeycloakId = keycloakUserId == null ? Optional.empty()
                : userRepository.findByKeycloakUserIdAndDeletedFalse(keycloakUserId);
        return byKeycloakId.or(() -> username == null ? Optional.empty() : findByIdentifier(normalize(username)));
    }

    /** Rejects locked, disabled, blocked and unverified accounts. Call only after the credential is proven. */
    public void ensureCanSignIn(User user) {
        if (user.isLocked()) {
            throw ErrorCode.ACCOUNT_LOCKED.raise();
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw ErrorCode.ACCOUNT_NOT_VERIFIED.raise();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ErrorCode.ACCOUNT_DISABLED.raise();
        }
    }

    /** Tenants and security guards use the mobile app; the web portal is for staff and admins. */
    public void ensureAllowedOnClient(User user, ClientSource clientSource) {
        if (clientSource == ClientSource.WEB && Role.MOBILE_ONLY.contains(user.primaryRole())) {
            throw ErrorCode.MOBILE_APP_ONLY.raise();
        }
    }

    /** Field-level update, so a concurrent edit of the user is not overwritten. */
    public void markPasswordChanged(String userId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(userId)),
                new Update().set("passwordChangedAt", now).set("updatedAt", now).inc("version", 1), User.class);
    }
}
