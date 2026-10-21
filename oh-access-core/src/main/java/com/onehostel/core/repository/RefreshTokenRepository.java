package com.onehostel.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.onehostel.core.model.RefreshTokenDoc;

@Repository
public interface RefreshTokenRepository extends OneHostelRepository<RefreshTokenDoc, String> {

    Optional<RefreshTokenDoc> findByTokenHashAndIsRevokedFalse(String tokenHash);

    List<RefreshTokenDoc> findAllByUserIdAndIsRevokedFalse(String userId);

    void deleteAllByUserId(String userId);
}