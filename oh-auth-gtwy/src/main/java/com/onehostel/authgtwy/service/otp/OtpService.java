package com.onehostel.service.otp;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.onehostel.constants.CommonEnumHelper.OtpChannel;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.GlobalConstants;
import com.onehostel.dto.auth.OtpSendRequest;
import com.onehostel.dto.auth.OtpSendResponse;
import com.onehostel.exception.ErrorCode;
import com.onehostel.model.OtpChallenge;
import com.onehostel.model.User;
import com.onehostel.service.LoginIdentifier;
import com.onehostel.service.LoginUserFinder;

import lombok.RequiredArgsConstructor;

/**
 * OTP login for owners and tenants: {@link #send} issues a code, /login with authMode OTP calls
 * {@link #verify}. Limits: a code lives OTP_VALIDITY, allows OTP_MAX_VERIFY_ATTEMPTS guesses,
 * and an identifier gets at most OTP_MAX_SENDS_PER_WINDOW codes per OTP_SEND_WINDOW. Sending a
 * new code cancels the previous one.
 *
 * Unknown identifiers get the same response and are counted the same way; a challenge is
 * stored with the hash of a code nobody received, so verification simply never succeeds.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;
    private final OtpSender otpSender;
    private final LoginUserFinder loginUserFinder;
    private final SecureRandom random = new SecureRandom();

    public OtpSendResponse send(OtpSendRequest request) {
        requireOtpRole(request.userType());
        LoginIdentifier identifier = LoginIdentifier.parse(request.identifier());
        String key = challengeKey(request.userType(), identifier);
        Instant now = Instant.now();

        long recentSends = mongoTemplate.count(new Query(Criteria.where("challengeKey").is(key)
                .and("createdAt").gt(now.minus(GlobalConstants.OTP_SEND_WINDOW))), OtpChallenge.class);
        if (recentSends >= GlobalConstants.OTP_MAX_SENDS_PER_WINDOW) {
            throw ErrorCode.TOO_MANY_REQUESTS.raise();
        }
        mongoTemplate.updateMulti(new Query(Criteria.where("challengeKey").is(key).and("consumed").is(false)),
                Update.update("consumed", true), OtpChallenge.class);

        Optional<String> destination = loginUserFinder.find(identifier, request.userType())
                .filter(User::canLogIn)
                .map(user -> request.channel() == OtpChannel.SMS ? user.getPhoneNumber() : user.getEmail());

        String code = newCode();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setChallengeKey(key);
        challenge.setCodeHash(passwordEncoder.encode(destination.isPresent() ? code : newCode()));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(GlobalConstants.OTP_VALIDITY));
        challenge.setPurgeAt(now.plus(GlobalConstants.OTP_SEND_WINDOW));
        mongoTemplate.insert(challenge);

        destination.ifPresent(to -> otpSender.send(request.channel(), to, code));
        return new OtpSendResponse(GlobalConstants.OTP_VALIDITY.toSeconds());
    }

    /** The account the code was sent for. Any failure is INVALID_OTP. */
    public User verify(String rawIdentifier, String code, Role role) {
        requireOtpRole(role);
        LoginIdentifier identifier = LoginIdentifier.parse(rawIdentifier);
        if (!code.matches(GlobalConstants.OTP_REGEX)) {
            throw ErrorCode.INVALID_OTP.raise();
        }
        Instant now = Instant.now();

        // Counting the attempt before comparing caps guesses even under concurrent requests.
        Query live = new Query(Criteria.where("challengeKey").is(challengeKey(role, identifier))
                .and("consumed").is(false)
                .and("expiresAt").gt(now)
                .and("attempts").lt(GlobalConstants.OTP_MAX_VERIFY_ATTEMPTS))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        OtpChallenge challenge = mongoTemplate.findAndModify(live, new Update().inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true), OtpChallenge.class);
        if (challenge == null || !passwordEncoder.matches(code, challenge.getCodeHash())) {
            throw ErrorCode.INVALID_OTP.raise();
        }

        long used = mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(challenge.getId()).and("consumed").is(false)),
                Update.update("consumed", true), OtpChallenge.class).getModifiedCount();
        if (used == 0) {
            throw ErrorCode.INVALID_OTP.raise();
        }
        return loginUserFinder.find(identifier, role).orElseThrow(() -> ErrorCode.INVALID_OTP.raise());
    }

    private static void requireOtpRole(Role role) {
        if (role == Role.SUPER_ADMIN) {
            throw ErrorCode.BAD_REQUEST.raise("OTP login is only for hostel owners and tenants.");
        }
    }

    private static String challengeKey(Role role, LoginIdentifier identifier) {
        return role.name() + ":" + identifier.value();
    }

    private String newCode() {
        return String.format("%0" + GlobalConstants.OTP_LENGTH + "d",
                random.nextInt((int) Math.pow(10, GlobalConstants.OTP_LENGTH)));
    }
}
