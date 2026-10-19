package com.onehostel.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.onehostel.constants.CommonEnumHelper.AuthMode;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.GlobalConstants;
import com.onehostel.dto.auth.LoginRequest;
import com.onehostel.dto.auth.LoginResponse;
import com.onehostel.dto.auth.UserProfile;
import com.onehostel.exception.ErrorCode;
import com.onehostel.model.User;
import com.onehostel.repository.UserRepository;
import com.onehostel.security.AuthenticatedUser;
import com.onehostel.security.JwtService;
import com.onehostel.service.otp.OtpService;

import lombok.extern.slf4j.Slf4j;

/**
 * The single login: every mode ends in {@link #completeLogin(User)} with the same checks, token
 * and response. The role always comes from the account; {@code userType} only says which kind of
 * account to look for, and a wrong one is answered exactly like an unknown identifier.
 *
 * Account state (status, lock) is checked only after the credential is proven, so a guesser
 * cannot learn whether an account exists or is disabled.
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginUserFinder loginUserFinder;
    private final OtpService otpService;

    /** Checked when the account is unknown, so response time does not reveal which accounts exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokenService, LoginUserFinder loginUserFinder, OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginUserFinder = loginUserFinder;
        this.otpService = otpService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** The response body, plus the refresh token for the controller to put in the cookie. */
    public record AuthResult(LoginResponse response, String refreshToken) {
    }

    public AuthResult login(LoginRequest request) {
        Role role = request.userType();
        AuthMode mode = request.authMode();
        if (mode == AuthMode.PASSCODE && role != Role.SUPER_ADMIN) {
            throw ErrorCode.BAD_REQUEST.raise("PASSCODE login is only for super admins.");
        }
        if (mode == AuthMode.OTP) {
            return completeLogin(otpService.verify(request.identifier(), request.secret(), role));
        }

        User user = loginUserFinder.find(LoginIdentifier.parse(request.identifier()), role).orElse(null);
        String hash = user == null ? null : mode == AuthMode.PASSCODE ? user.getPasscodeHash() : user.getPasswordHash();
        boolean wellFormed = mode != AuthMode.PASSCODE || request.secret().matches(GlobalConstants.PASSCODE_REGEX);

        boolean matches = passwordEncoder.matches(request.secret(), hash != null ? hash : dummyHash);
        if (hash == null || !wellFormed || !matches) {
            log.info("Failed {} {} login for {}", role, mode, request.identifier());
            throw ErrorCode.INVALID_CREDENTIALS.raise();
        }
        return completeLogin(user);
    }

    /** Call only after the user has proven who they are. */
    public AuthResult completeLogin(User user) {
        requireLoginAllowed(user);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        AuthResult result = issueTokens(user, null);
        log.info("User {} logged in as {}", user.getId(), user.getRole());
        return result;
    }

    /** New access token and rotated refresh token. Accounts that can no longer log in lose the session. */
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.consume(refreshToken);
        User user = userRepository.findById(rotation.userId()).filter(User::canLogIn).orElse(null);
        if (user == null) {
            refreshTokenService.revokeFamily(rotation.familyId());
            throw ErrorCode.UNAUTHORIZED.raise();
        }
        return issueTokens(user, rotation.familyId());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revokeSessionOf(refreshToken);
    }

    public UserProfile currentUser(AuthenticatedUser principal) {
        return userRepository.findById(principal.id())
                .filter(User::canLogIn)
                .map(UserProfile::from)
                .orElseThrow(() -> ErrorCode.UNAUTHORIZED.raise());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireLoginAllowed(User user) {
        if (user.isLocked()) {
            log.info("Login refused, account locked: {}", user.getId());
            throw ErrorCode.ACCOUNT_LOCKED.raise();
        }
        switch (user.getStatus()) {
            case ACTIVE -> {
            }
            case PENDING_VERIFICATION -> throw ErrorCode.ACCOUNT_NOT_VERIFIED.raise();
            case INACTIVE, BLOCKED -> {
                log.info("Login refused, account {}: {}", user.getStatus(), user.getId());
                throw ErrorCode.ACCOUNT_DISABLED.raise();
            }
        }
    }

    private AuthResult issueTokens(User user, String familyId) {
        JwtService.IssuedToken accessToken = jwtService.issue(user);
        String refreshToken = refreshTokenService.issue(user.getId(), familyId);
        return new AuthResult(LoginResponse.bearer(accessToken.token(), accessToken.expiresInSeconds(),
                UserProfile.from(user)), refreshToken);
    }
}
