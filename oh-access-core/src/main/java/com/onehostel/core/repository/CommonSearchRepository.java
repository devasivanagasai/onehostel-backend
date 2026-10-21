package com.onehostel.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.onehostel.core.constants.EnumHelper.EntityTypeEnum;
import com.onehostel.core.model.CommonSearch;

@Repository
public interface CommonSearchRepository extends MongoRepository<CommonSearch, String> {

    Optional<CommonSearch> findByEntityModelKeyAndEntityEnum(String entityModelKey, EntityTypeEnum entityEnum);

    List<CommonSearch> findAllByEntityEnumAndOrganizationId(EntityTypeEnum entityEnum, String organizationId);

    Page<CommonSearch> findAllByEntityEnumAndOrganizationId(EntityTypeEnum entityEnum, String organizationId, Pageable pageable);

    List<CommonSearch> findAllByEntityEnumAndHostelId(EntityTypeEnum entityEnum, String hostelId);

    void deleteByEntityModelKeyAndEntityEnum(String entityModelKey, EntityTypeEnum entityEnum);
}