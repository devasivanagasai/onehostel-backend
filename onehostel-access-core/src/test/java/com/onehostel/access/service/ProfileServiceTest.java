package com.onehostel.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.onehostel.access.client.KeycloakAdminClient;
import com.onehostel.access.dto.ProfileDtos.ProfileResponse;
import com.onehostel.access.dto.ProfileDtos.ProfileUpdateRequest;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.model.User;
import com.onehostel.access.repository.UserRepository;
import com.onehostel.access.vo.MobileNumber;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.exception.HostelException;
import com.onehostel.common.service.AuditRecorder;

class ProfileServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private ProfileService service;
    private User me;

    @BeforeEach
    void setUp() {
        service = new ProfileService(users, keycloak, mock(AuditRecorder.class));
        me = user("01JME", "super.admin@onehostel.local", "9000000002");
        me.setUsername("super.admin");
        me.setKeycloakUserId("kc-me");
        me.setFirstName("Super");
        me.setLastName("Admin");
        when(users.findById("01JME")).thenReturn(Optional.of(me));
        when(users.findByEmailAndDeletedFalse(anyString())).thenReturn(Optional.empty());
        when(users.findByMobileNumber(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(keycloak.requireUserId("kc-me", "super.admin")).thenReturn("kc-me");
    }

    private static User user(String id, String email, String mobile) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setMobileNumber(new MobileNumber("+91", mobile));
        return user;
    }

    private static Object errorCode(Throwable e) {
        return ((HostelException) e).getErrorCode();
    }

    @Test
    void returnsTheStoredAccount() {
        ProfileResponse profile = service.get("01JME");

        assertThat(profile.firstName()).isEqualTo("Super");
        assertThat(profile.email()).isEqualTo("super.admin@onehostel.local");
        assertThat(profile.mobileNumber()).isEqualTo("9000000002");
    }

    @Test
    void savesToTheDatabaseAndKeycloak() {
        ProfileResponse profile = service.update("01JME",
                new ProfileUpdateRequest(" Siva ", "Boddu", " Siva@OneHostel.com ", "+91", "9848012345"));

        assertThat(profile.firstName()).isEqualTo("Siva");
        assertThat(profile.email()).isEqualTo("siva@onehostel.com");
        assertThat(me.getMobileNumber()).isEqualTo(new MobileNumber("+91", "9848012345"));
        verify(keycloak).updateProfile("kc-me", "Siva", "Boddu", "siva@onehostel.com");
        verify(users).save(me);
    }

    @Test
    void aMobileOnlyChangeDoesNotTouchKeycloak() {
        service.update("01JME", new ProfileUpdateRequest("Super", "Admin", "super.admin@onehostel.local", "+91", "9848012345"));

        verify(keycloak, never()).updateProfile(anyString(), anyString(), anyString(), anyString());
        verify(users).save(me);
    }

    @Test
    void nothingChangedWritesNothing() {
        service.update("01JME", new ProfileUpdateRequest("Super", "Admin", "super.admin@onehostel.local", "+91", "9000000002"));

        verify(users, never()).save(any());
        verify(keycloak, never()).updateProfile(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void anEmailOfAnotherAccountIsRefused() {
        when(users.findByEmailAndDeletedFalse("taken@onehostel.com")).thenReturn(Optional.of(user("01JOTHER", "taken@onehostel.com", "9111111111")));

        assertThatThrownBy(() -> service.update("01JME", new ProfileUpdateRequest("Super", "Admin", "taken@onehostel.com", "+91", "9000000002")))
                .extracting(ProfileServiceTest::errorCode).isEqualTo(ErrorCode.EMAIL_IN_USE);
        verify(users, never()).save(any());
    }

    @Test
    void aMobileOfAnotherAccountIsRefused() {
        when(users.findByMobileNumber("9111111111")).thenReturn(Optional.of(user("01JOTHER", "other@onehostel.com", "9111111111")));

        assertThatThrownBy(() -> service.update("01JME", new ProfileUpdateRequest("Super", "Admin", "super.admin@onehostel.local", "+91", "9111111111")))
                .extracting(ProfileServiceTest::errorCode).isEqualTo(ErrorCode.MOBILE_IN_USE);
    }

    @Test
    void unknownCallerIsNotFound() {
        assertThatThrownBy(() -> service.get("01JNOBODY"))
                .extracting(ProfileServiceTest::errorCode).isEqualTo(CommonErrorCode.ENTITY_NOT_FOUND);
    }
}
