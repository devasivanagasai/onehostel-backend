package com.onehostel.common.vo;

import com.onehostel.common.constants.CommonEnums.AndOrType;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** issuanceSearch in the request envelope: conditions joined by AND (default) or OR. */
public record FilterGroup(AndOrType andOrType, @Valid @Size(max = 30) List<FilterQueryVO> filterQueryVO) {
    public boolean isEmpty() {
        return filterQueryVO == null || filterQueryVO.isEmpty();
    }
}
