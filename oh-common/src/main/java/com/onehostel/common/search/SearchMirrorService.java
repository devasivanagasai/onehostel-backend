package com.onehostel.service.search;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.onehostel.constants.CommonEnumHelper.EntityType;
import com.onehostel.model.HostelCommonSearch;
import com.onehostel.repository.HostelCommonSearchRepository;

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
