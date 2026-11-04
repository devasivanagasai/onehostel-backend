package com.onehostel.common.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onehostel.common.constants.CommonEnums.ClientSource;
import com.onehostel.common.constants.CommonEnums.SortOrder;
import com.onehostel.common.vo.FilterGroup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The request envelope the Angular app sends. Controllers normally bind the inner type directly
 * (RequestEnvelopeAdvice unwraps requestObject) and read the rest through {@link RequestEnvelope#current()};
 * list endpoints take the whole envelope for its paging, sort and filter fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommonRequestObject<T>(
        @Valid T requestObject,
        @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$", message = "requestId must be 8-64 letters, digits or dashes") String requestId,
        ClientSource clientSource,
        @Size(max = 32) String appVersion,
        @Size(max = 35) String locale,
        Instant requestedAt,
        @Min(0) Integer page,
        @Min(1) @Max(200) Integer size,
        @Size(max = 64) String sortField,
        SortOrder sortOrder,
        Boolean paginationRequired,
        @Valid FilterGroup issuanceSearch) {
}
