package com.onehostel.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.onehostel.model.User;

public interface UserRepository extends MongoRepository<User, String> {

    /** {@code email} must already be lower-cased (see {@link com.onehostel.service.AuthService#normalizeEmail}). */
    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);
}
