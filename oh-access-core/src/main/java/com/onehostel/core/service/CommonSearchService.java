package com.onehostel.core.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.onehostel.core.constants.EnumHelper.EntityTypeEnum;
import com.onehostel.core.model.CommonSearch;

public interface CommonSearchService {

    void sync(CommonSearch commonSearch);

    void remove(String entityModelKey, EntityTypeEnum entityEnum);

    List<CommonSearch> getAllByOrg(EntityTypeEnum entityEnum, String organizationId);

    Page<CommonSearch> getAllByOrg(EntityTypeEnum entityEnum, String organizationId, Pageable pageable);

    List<CommonSearch> getAllByHostel(EntityTypeEnum entityEnum, String hostelId);
}