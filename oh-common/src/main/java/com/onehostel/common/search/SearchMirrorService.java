package com.onehostel.common.search;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.onehostel.common.constants.CommonEnumHelper.EntityType;

import lombok.RequiredArgsConstructor;

/** The only writer of HostelCommonSearch: one row per (entityEnum, entityModelKey). */
@Service
@RequiredArgsConstructor
public class SearchMirrorService {

    private final HostelCommonSearchRepository repository;

    public void upsert(HostelCommonSearch row) {
        repository.findByEntityModelKeyAndEntityEnum(row.getEntityModelKey(), row.getEntityEnum())
                .ifPresent(existing -> row.setId(existing.getId()));
        row.setUpdatedDateTime(Instant.now());
        repository.save(row);
    }

    public void delete(EntityType type, String entityModelKey) {
        repository.deleteByEntityModelKeyAndEntityEnum(entityModelKey, type);
    }
}
