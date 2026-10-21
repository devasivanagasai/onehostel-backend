package com.onehostel.gateway.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.onehostel.core.model.RefreshTokenDoc;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveRefreshTokenRepository extends ReactiveMongoRepository<RefreshTokenDoc, String> {

    Mono<RefreshTokenDoc> findByTokenHashAndIsRevokedFalse(String tokenHash);

    Mono<Void> deleteAllByUserId(String userId);

    Flux<RefreshTokenDoc> findAllByUserIdAndIsRevokedFalse(String userId);
}