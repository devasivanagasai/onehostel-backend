package com.onehostel.dto.helper;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.onehostel.constants.CommonEnumHelper.ClientSource;
import com.onehostel.constants.CommonEnumHelper.SortOrder;
import com.onehostel.constants.GlobalConstants;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CommonRequestObject<T>(
        @NotNull @Valid T requestObject,
        @Pattern(regexp = GlobalConstants.REQUEST_ID_REGEX) String requestId,
        ClientSource clientSource,
        @Size(max = 32) String appVersion,
        @Size(max = 35) String locale,
        Instant requestedAt,
        @Min(0) Integer page,
        @Min(1) @Max(GlobalConstants.MAX_PAGE_SIZE) Integer size,
        @Size(max = 64) String sortField,
        SortOrder sortOrder,
        Boolean paginationRequired) {

    public static <T> CommonRequestObject<T> of(T requestObject) {
        return new CommonRequestObject<>(requestObject, null, null, null, null, null, null, null, null, null, null);
    }

    public boolean isPaginationRequired() {
        return !Boolean.FALSE.equals(paginationRequired);
    }

    public Pageable toPageable(Set<String> sortableFields) {
        if (!isPaginationRequired()) {
            return Pageable.unpaged(sort(sortableFields));
        }
        int pageNumber = page != null ? page : GlobalConstants.DEFAULT_PAGE;
        int pageSize = size != null ? size : GlobalConstants.DEFAULT_PAGE_SIZE;
        return PageRequest.of(pageNumber, pageSize, sort(sortableFields));
    }

    private Sort sort(Set<String> sortableFields) {
        String field = sortField != null && sortableFields.contains(sortField)
                ? sortField
                : GlobalConstants.DEFAULT_SORT_FIELD;
        Sort.Direction direction = sortOrder != null
                ? sortOrder.toDirection()
                : GlobalConstants.DEFAULT_SORT_DIRECTION;
        return Sort.by(direction, field);
    }
}
