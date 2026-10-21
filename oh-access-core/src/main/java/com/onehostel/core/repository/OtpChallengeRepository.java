package com.onehostel.core.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.onehostel.core.constants.EnumHelper.OtpChannel;
import com.onehostel.core.model.OtpChallengeDoc;

@Repository
public interface OtpChallengeRepository extends OneHostelRepository<OtpChallengeDoc, String> {

    Optional<OtpChallengeDoc> findTopByIdentifierAndChannelAndIsConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String identifier, OtpChannel channel, Instant now);
}