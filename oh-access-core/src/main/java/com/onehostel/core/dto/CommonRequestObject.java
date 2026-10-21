package com.onehostel.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.core.constants.GlobalConstants;
import com.onehostel.core.constants.EnumHelper.ClientSource;
import com.onehostel.core.constants.EnumHelper.SortOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonRequestObject<T> {

    @NotNull(message = "requestObject must not be null")
    @Valid
    private T requestObject;

    @Pattern(regexp = GlobalConstants.REQUEST_ID_REGEX, message = "requestId must match regex " + GlobalConstants.REQUEST_ID_REGEX)
    private String requestId;

    private ClientSource clientSource;

    @Size(max = 32, message = "appVersion cannot exceed 32 characters")
    private String appVersion;

    @Size(max = 35, message = "locale cannot exceed 35 characters")
    private String locale;

    private Instant requestedAt;

    @Min(value = 0, message = "page must be greater than or equal to 0")
    private Integer page;

    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 100, message = "size cannot exceed 100")
    private Integer size;

    @Size(max = 64, message = "sortField cannot exceed 64 characters")
    private String sortField;

    private SortOrder sortOrder;

    private Boolean paginationRequired;
}