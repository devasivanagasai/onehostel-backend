package com.onehostel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;
import com.onehostel.config.AppProperties;
import com.onehostel.exception.ErrorCode;
import com.onehostel.exception.OneHostelException;
import com.onehostel.model.RefreshToken;

class RefreshTokenServiceTest {

    private MongoTemplate mongoTemplate;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        AppProperties properties = new AppProperties(new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Jwt("secret", Duration.ofMinutes(15), Duration.ofDays(7), "onehostel"), null,
                new AppProperties.RateLimit(10));
        service = new RefreshTokenService(mongoTemplate, properties);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(RefreshToken.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    @Test
    void onlyTheHashIsStored() {
        String raw = service.issue("u1", null);

        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(mongoTemplate).insert(stored.capture());
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
        assertThat(stored.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(stored.getValue().getFamilyId()).isNotBlank();
        assertThat(stored.getValue().getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    }

    @Test
    void validTokenIsConsumedOnce() {
        givenStored(token(null, Instant.now().plusSeconds(60)));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(RefreshToken.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        RefreshTokenService.Rotation rotation = service.consume("raw");

        assertThat(rotation.userId()).isEqualTo("u1");
        assertThat(rotation.familyId()).isEqualTo("fam-1");
    }

    @Test
    void reusingARevokedTokenRevokesTheWholeSession() {
        givenStored(token(Instant.now().minusSeconds(5), Instant.now().plusSeconds(60)));

        assertUnauthorized(() -> service.consume("raw"));
        assertFamilyRevoked();
    }

    @Test
    void losingAConcurrentRefreshAlsoRevokesTheSession() {
        givenStored(token(null, Instant.now().plusSeconds(60)));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(RefreshToken.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertUnauthorized(() -> service.consume("raw"));
        assertFamilyRevoked();
    }

    @Test
    void expiredOrUnknownTokensAreRejected() {
        givenStored(token(null, Instant.now().minusSeconds(1)));
        assertUnauthorized(() -> service.consume("raw"));

        assertUnauthorized(() -> service.consume(null));
    }

    private void assertFamilyRevoked() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateMulti(query.capture(), any(Update.class), eq(RefreshToken.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("fam-1");
    }

    private void givenStored(RefreshToken token) {
        when(mongoTemplate.findOne(any(Query.class), eq(RefreshToken.class))).thenReturn(token);
    }

    private static RefreshToken token(Instant revokedAt, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId("r1");
        token.setUserId("u1");
        token.setFamilyId("fam-1");
        token.setRevokedAt(revokedAt);
        token.setExpiresAt(expiresAt);
        return token;
    }

    private static void assertUnauthorized(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(OneHostelException.class)
                .extracting(ex -> ((OneHostelException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
