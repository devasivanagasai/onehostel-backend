package com.onehostel.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.model.User;
import com.onehostel.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Finds the account a login identifier names, restricted to one role: an account of another
 * role is answered exactly like a missing one. Only super admins have usernames.
 */
@Component
@RequiredArgsConstructor
public class LoginUserFinder {

    private final UserRepository userRepository;

    public Optional<User> find(LoginIdentifier identifier, Role role) {
        Optional<User> user = switch (identifier.kind()) {
            case EMAIL -> userRepository.findByEmail(identifier.value());
            case PHONE -> userRepository.findByPhoneNumber(identifier.value());
            case USERNAME -> role == Role.SUPER_ADMIN ? userRepository.findByUsername(identifier.value())
                    : Optional.empty();
        };
        return user.filter(found -> found.getRole() == role);
    }
}
