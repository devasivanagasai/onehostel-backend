package com.onehostel.service.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mongodb.client.result.UpdateResult;
import com.onehostel.constants.CommonEnumHelper.OtpChannel;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.dto.auth.OtpSendRequest;
import com.onehostel.exception.ErrorCode;
import com.onehostel.exception.OneHostelException;
import com.onehostel.model.OtpChallenge;
import com.onehostel.model.User;
import com.onehostel.service.LoginIdentifier;
import com.onehostel.service.LoginUserFinder;

class OtpServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private MongoTemplate mongoTemplate;
    private OtpSender sender;
    private LoginUserFinder loginUserFinder;
    private OtpService service;
    private User owner;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        sender = mock(OtpSender.class);
        loginUserFinder = mock(LoginUserFinder.class);
        service = new OtpService(mongoTemplate, encoder, sender, loginUserFinder);

        owner = new User("Owner", "owner@x.com", "hash", Role.HOSTEL_OWNER);
        owner.setId("o1");
        owner.setPhoneNumber("9876500001");
        when(loginUserFinder.find(any(LoginIdentifier.class), eq(Role.HOSTEL_OWNER))).thenReturn(Optional.of(owner));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(OtpChallenge.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
    }

    @Test
    void sendsAHashedCodeToTheAccountsPhone() {
        service.send(new OtpSendRequest("9876500001", OtpChannel.SMS, Role.HOSTEL_OWNER));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(OtpChannel.SMS), eq("9876500001"), code.capture());
        ArgumentCaptor<OtpChallenge> stored = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(mongoTemplate).insert(stored.capture());

        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(stored.getValue().getCodeHash()).isNotEqualTo(code.getValue());
        assertThat(encoder.matches(code.getValue(), stored.getValue().getCodeHash())).isTrue();
        assertThat(stored.getValue().getChallengeKey()).isEqualTo("HOSTEL_OWNER:9876500001");
    }

    @Test
    void emailChannelUsesTheAccountsEmail() {
        service.send(new OtpSendRequest("9876500001", OtpChannel.EMAIL, Role.HOSTEL_OWNER));

        verify(sender).send(eq(OtpChannel.EMAIL), eq("owner@x.com"), anyString());
    }

    @Test
    void unknownAccountIsCountedButNothingIsSent() {
        when(loginUserFinder.find(any(LoginIdentifier.class), eq(Role.HOSTEL_OWNER))).thenReturn(Optional.empty());

        service.send(new OtpSendRequest("9000000000", OtpChannel.SMS, Role.HOSTEL_OWNER));

        verify(mongoTemplate).insert(any(OtpChallenge.class));
        verify(sender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void sendLimitPerWindowIsEnforced() {
        when(mongoTemplate.count(any(Query.class), eq(OtpChallenge.class))).thenReturn(3L);

        assertFails(() -> service.send(new OtpSendRequest("9876500001", OtpChannel.SMS, Role.HOSTEL_OWNER)),
                ErrorCode.TOO_MANY_REQUESTS);
        verify(sender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void superAdminsCannotUseOtp() {
        assertFails(() -> service.send(new OtpSendRequest("admin@x.com", OtpChannel.EMAIL, Role.SUPER_ADMIN)),
                ErrorCode.BAD_REQUEST);
        assertFails(() -> service.verify("admin@x.com", "123456", Role.SUPER_ADMIN), ErrorCode.BAD_REQUEST);
    }

    @Test
    void malformedCodeNeverReachesTheDatabase() {
        assertFails(() -> service.verify("9876500001", "12ab56", Role.HOSTEL_OWNER), ErrorCode.INVALID_OTP);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(OtpChallenge.class));
    }

    @Test
    void lockedAccountGetsNoCode() {
        owner.setLocked(true);

        service.send(new OtpSendRequest("9876500001", OtpChannel.SMS, Role.HOSTEL_OWNER));

        verify(sender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void correctCodeLogsInOnce() {
        givenLiveChallenge("123456");
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(OtpChallenge.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        User user = service.verify("9876500001", "123456", Role.HOSTEL_OWNER);

        assertThat(user).isSameAs(owner);
    }

    @Test
    void wrongCodeIsRejected() {
        givenLiveChallenge("123456");

        assertFails(() -> service.verify("9876500001", "654321", Role.HOSTEL_OWNER),
                ErrorCode.INVALID_OTP);
    }

    @Test
    void codeAlreadyUsedByAConcurrentRequestIsRejected() {
        givenLiveChallenge("123456");
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(OtpChallenge.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertFails(() -> service.verify("9876500001", "123456", Role.HOSTEL_OWNER),
                ErrorCode.INVALID_OTP);
    }

    @Test
    void onlyUnexpiredUnusedChallengesUnderTheAttemptLimitAreTried() {
        assertFails(() -> service.verify("9876500001", "123456", Role.HOSTEL_OWNER),
                ErrorCode.INVALID_OTP);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class),
                eq(OtpChallenge.class));
        Document filter = query.getValue().getQueryObject();
        assertThat(filter.get("challengeKey")).isEqualTo("HOSTEL_OWNER:9876500001");
        assertThat(filter.get("consumed")).isEqualTo(false);
        assertThat(filter.get("attempts", Document.class).get("$lt")).isEqualTo(5);
        assertThat(filter).containsKey("expiresAt");
    }

    private void givenLiveChallenge(String code) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setId("c1");
        challenge.setCodeHash(encoder.encode(code));
        challenge.setAttempts(1);
        challenge.setExpiresAt(Instant.now().plusSeconds(60));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(OtpChallenge.class))).thenReturn(challenge);
    }

    private static void assertFails(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(OneHostelException.class)
                .extracting(ex -> ((OneHostelException) ex).getErrorCode())
                .isEqualTo(expected);
    }
}
