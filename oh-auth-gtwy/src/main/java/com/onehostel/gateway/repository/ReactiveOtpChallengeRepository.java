package com.onehostel.gateway.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.onehostel.core.constants.EnumHelper.OtpPurpose;
import com.onehostel.core.model.OtpChallengeDoc;

import reactor.core.publisher.Mono;

@Repository
public interface ReactiveOtpChallengeRepository extends ReactiveMongoRepository<OtpChallengeDoc, String> {

    Mono<OtpChallengeDoc> findTopByIdentifierAndPurposeAndIsConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String identifier, OtpPurpose purpose, Instant now);

    Mono<Long> countByIdentifierAndPurposeAndCreatedAtAfter(String identifier, OtpPurpose purpose, Instant since);
}
