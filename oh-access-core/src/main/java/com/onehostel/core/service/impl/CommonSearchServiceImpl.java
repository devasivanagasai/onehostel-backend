package com.onehostel.core.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.onehostel.core.constants.EnumHelper.EntityTypeEnum;
import com.onehostel.core.model.CommonSearch;
import com.onehostel.core.repository.CommonSearchRepository;
import com.onehostel.core.service.CommonSearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommonSearchServiceImpl implements CommonSearchService {

    private final CommonSearchRepository commonSearchRepository;

    @Override
    public void sync(CommonSearch commonSearch) {
        if (commonSearch == null || commonSearch.getEntityModelKey() == null || commonSearch.getEntityEnum() == null) {
            log.warn("Skipping common search sync due to incomplete entity metadata");
            return;
        }

        Optional<CommonSearch> existing = commonSearchRepository.findByEntityModelKeyAndEntityEnum(
                commonSearch.getEntityModelKey(), commonSearch.getEntityEnum());

        if (existing.isPresent()) {
            commonSearch.setId(existing.get().getId());
            commonSearch.setCreatedAt(existing.get().getCreatedAt());
            commonSearch.setCreatedBy(existing.get().getCreatedBy());
            commonSearch.setUpdatedAt(Instant.now());
        }

        commonSearchRepository.save(commonSearch);
    }

    @Override
    public void remove(String entityModelKey, EntityTypeEnum entityEnum) {
        commonSearchRepository.deleteByEntityModelKeyAndEntityEnum(entityModelKey, entityEnum);
    }

    @Override
    public List<CommonSearch> getAllByOrg(EntityTypeEnum entityEnum, String organizationId) {
        return commonSearchRepository.findAllByEntityEnumAndOrganizationId(entityEnum, organizationId);
    }

    @Override
    public Page<CommonSearch> getAllByOrg(EntityTypeEnum entityEnum, String organizationId, Pageable pageable) {
        return commonSearchRepository.findAllByEntityEnumAndOrganizationId(entityEnum, organizationId, pageable);
    }

    @Override
    public List<CommonSearch> getAllByHostel(EntityTypeEnum entityEnum, String hostelId) {
        return commonSearchRepository.findAllByEntityEnumAndHostelId(entityEnum, hostelId);
    }
}