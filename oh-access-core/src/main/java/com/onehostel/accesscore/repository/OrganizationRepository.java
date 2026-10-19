package com.onehostel.accesscore.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.onehostel.accesscore.model.Organization;

public interface OrganizationRepository extends MongoRepository<Organization, String> {

    boolean existsByName(String name);
}
