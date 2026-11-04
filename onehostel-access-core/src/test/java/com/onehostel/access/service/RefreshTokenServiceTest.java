package com.onehostel.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.model.RefreshToken;
import com.onehostel.access.repository.RefreshTokenRepository;
import com.onehostel.common.exception.HostelException;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(repository);

    private RefreshToken stored(String raw, boolean revoked, String replacedBy, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUserId("01JUSER");
        token.setTokenHash(RefreshTokenService.hash(raw));
        token.setRevoked(revoked);
        token.setReplacedByTokenHash(replacedBy);
        token.setExpiresAt(expiresAt);
        when(repository.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void liveTokenIsUsable() {
        RefreshToken token = stored("rt-1", false, null, Instant.now().plusSeconds(600));

        assertThat(service.requireUsable("rt-1")).isSameAs(token);
    }

    @Test
    void reusingASpentTokenRevokesEverySessionOfTheUser() {
        stored("rt-old", true, "hash-of-new", Instant.now().plusSeconds(600));
        RefreshToken otherSession = new RefreshToken();
        otherSession.setKeycloakSessionId("sid-2");
        when(repository.findByUserIdAndRevokedFalse("01JUSER")).thenReturn(List.of(otherSession));

        assertThatThrownBy(() -> service.requireUsable("rt-old"))
                .satisfies(e -> assertThat(((HostelException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        assertThat(otherSession.isRevoked()).isTrue();
    }

    @Test
    void expiredOrUnknownTokensAreRefused() {
        RefreshToken expired = stored("rt-exp", false, null, Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.requireUsable("rt-exp")).isInstanceOf(HostelException.class);
        assertThat(expired.isRevoked()).isTrue();
        assertThatThrownBy(() -> service.requireUsable("never-issued")).isInstanceOf(HostelException.class);
    }
}
