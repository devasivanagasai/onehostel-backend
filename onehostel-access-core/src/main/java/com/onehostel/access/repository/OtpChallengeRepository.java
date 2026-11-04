package com.onehostel.access.repository;

import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.model.OtpChallenge;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OtpChallengeRepository extends MongoRepository<OtpChallenge, String> {

    long countByIdentifierAndPurposeAndCreatedAtAfter(String identifier, OtpPurpose purpose, Instant since);

    Optional<OtpChallenge> findFirstByIdentifierAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String identifier, OtpPurpose purpose, Instant now);
}
