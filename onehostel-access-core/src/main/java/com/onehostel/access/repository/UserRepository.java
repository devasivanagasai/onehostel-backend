package com.onehostel.access.repository;

import com.onehostel.access.model.User;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmailAndDeletedFalse(String email);

    @Query("{ 'mobileNumber.number': ?0, 'deleted': false }")
    Optional<User> findByMobileNumber(String number);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    Optional<User> findByKeycloakUserIdAndDeletedFalse(String keycloakUserId);
}
