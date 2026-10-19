package com.onehostel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.model.User;
import com.onehostel.repository.UserRepository;

class UserSeederTest {

    private UserRepository userRepository;
    private UserSeeder seeder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        AppProperties properties = new AppProperties(new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Jwt("s", Duration.ofMinutes(15), Duration.ofDays(7), "i"),
                new AppProperties.Bootstrap("admin@onehostel.com", "superadmin", "Admin@OneHostel2026", "246810",
                        "OneHostel Super Admin", false, false),
                new AppProperties.RateLimit(10));
        seeder = new UserSeeder(properties, userRepository, new BCryptPasswordEncoder(4));
    }

    @Test
    void usernameHeldByAnotherAccountIsLeftOffInsteadOfFailingStartup() {
        User old = new User("Old", "superadmin@onehostel.com", "hash", Role.SUPER_ADMIN);
        old.setId("old-id");
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(old));

        seeder.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@onehostel.com");
        assertThat(saved.getValue().getUsername()).isNull();
        assertThat(saved.getValue().getPasscodeHash()).isNotNull();
    }

    @Test
    void duplicateKeyFromTheDatabaseDoesNotStopStartup() {
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();
    }
}
