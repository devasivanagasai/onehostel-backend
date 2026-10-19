package com.onehostel.common.search;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.onehostel.common.constants.CommonEnumHelper.EntityType;

public interface HostelCommonSearchRepository extends MongoRepository<HostelCommonSearch, String> {

    Optional<HostelCommonSearch> findByEntityModelKeyAndEntityEnum(String entityModelKey, EntityType entityEnum);

    /** Tenant-isolated lookup: empty when the record belongs to another organization. */
    Optional<HostelCommonSearch> findByEntityModelKeyAndEntityEnumAndOrganizationId(String entityModelKey,
            EntityType entityEnum, String organizationId);

    void deleteByEntityModelKeyAndEntityEnum(String entityModelKey, EntityType entityEnum);
}
