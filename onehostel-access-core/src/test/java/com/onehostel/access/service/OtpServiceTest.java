package com.onehostel.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.onehostel.access.config.OtpProperties;
import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.helper.OtpDelivery;
import com.onehostel.access.model.OtpChallenge;
import com.onehostel.access.repository.OtpChallengeRepository;
import com.onehostel.common.exception.HostelException;

class OtpServiceTest {

    private final OtpChallengeRepository repository = mock(OtpChallengeRepository.class);
    private final OtpProperties properties = new OtpProperties(true, false, Duration.ofMinutes(5), 5, 3, Duration.ofMinutes(15));
    private final OtpService service = new OtpService(repository, mock(UserLookupService.class), new OtpDelivery(properties), properties);

    private OtpChallenge challenge(String code, int attempts) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setIdentifier("9848012345");
        challenge.setCodeHash(OtpService.hash("9848012345", code));
        challenge.setAttemptCount(attempts);
        challenge.setExpiresAt(Instant.now().plusSeconds(60));
        when(repository.findFirstByIdentifierAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                eq("9848012345"), eq(OtpPurpose.LOGIN), any())).thenReturn(Optional.of(challenge));
        return challenge;
    }

    private static ErrorCode codeOf(Throwable e) {
        return (ErrorCode) ((HostelException) e).getErrorCode();
    }

    @Test
    void rightCodePasses() {
        OtpChallenge challenge = challenge("123456", 0);

        assertThat(service.check("9848012345", OtpPurpose.LOGIN, "123456", false)).isSameAs(challenge);
    }

    @Test
    void wrongCodeCountsAnAttempt() {
        OtpChallenge challenge = challenge("123456", 0);

        assertThatThrownBy(() -> service.check("9848012345", OtpPurpose.LOGIN, "654321", false))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_OTP));
        assertThat(challenge.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void tooManyWrongCodesBurnTheChallenge() {
        OtpChallenge challenge = challenge("123456", 5);

        assertThatThrownBy(() -> service.check("9848012345", OtpPurpose.LOGIN, "123456", false))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TOO_MANY_WRONG_CODES));
        assertThat(challenge.isConsumed()).isTrue();
    }

    @Test
    void noOpenChallengeIsInvalidOtp() {
        assertThatThrownBy(() -> service.check("9000000000", OtpPurpose.LOGIN, "123456", true))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_OTP));
    }

    @Test
    void acceptAnyCodeStillNeedsAChallenge() {
        challenge("123456", 0);

        assertThat(service.check("9848012345", OtpPurpose.LOGIN, "000000", true)).isNotNull();
    }
}
