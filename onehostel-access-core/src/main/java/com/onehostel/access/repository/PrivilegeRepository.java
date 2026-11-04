package com.onehostel.access.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.onehostel.access.model.Privilege;

public interface PrivilegeRepository extends MongoRepository<Privilege, String> {

    Optional<Privilege> findByCode(String code);

    /** The highest bit taken so far, retired privileges included. */
    Optional<Privilege> findTopByOrderByBitDesc();
}
