package com.onehostel.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.onehostel.service.LoginIdentifier.Kind;

class LoginIdentifierTest {

    @Test
    void recognisesEmailPhoneAndUsername() {
        assertThat(LoginIdentifier.parse(" Owner@OneHostel.com "))
                .isEqualTo(new LoginIdentifier(Kind.EMAIL, "owner@onehostel.com"));
        assertThat(LoginIdentifier.parse("9876500001")).isEqualTo(new LoginIdentifier(Kind.PHONE, "9876500001"));
        assertThat(LoginIdentifier.parse("SuperAdmin")).isEqualTo(new LoginIdentifier(Kind.USERNAME, "superadmin"));
    }

    @Test
    void numbersThatAreNotIndianMobilesAreNotPhones() {
        assertThat(LoginIdentifier.parse("1234567890").kind()).isEqualTo(Kind.USERNAME);
        assertThat(LoginIdentifier.parse("98765").kind()).isEqualTo(Kind.USERNAME);
    }
}
