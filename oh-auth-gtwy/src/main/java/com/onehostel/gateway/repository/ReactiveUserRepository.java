package com.onehostel.gateway.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.onehostel.core.model.UserDoc;

import reactor.core.publisher.Mono;

@Repository
public interface ReactiveUserRepository extends ReactiveMongoRepository<UserDoc, String> {

    Mono<UserDoc> findByEmailAndIsDeletedFalse(String email);

    Mono<UserDoc> findByUserNameAndIsDeletedFalse(String userName);

    Mono<UserDoc> findByKeycloakUserIdAndIsDeletedFalse(String keycloakUserId);

    Mono<UserDoc> findFirstByMobileNumber_NumberAndIsDeletedFalse(String number);
}