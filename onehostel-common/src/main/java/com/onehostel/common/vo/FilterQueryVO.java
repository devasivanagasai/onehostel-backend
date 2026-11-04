package com.onehostel.common.vo;

import com.onehostel.common.constants.CommonEnums.FilterType;
import com.onehostel.common.constants.CommonEnums.OperatorEnum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One condition of a list filter. Field names match the Angular app's issuanceSearch.filterQueryVO.
 *
 * @param filterfield  entity property path, checked against the document class (e.g. "status", "address.city")
 * @param filtervalue  a string, number, boolean, or a list for IN / NOT_IN
 */
public record FilterQueryVO(
        @NotBlank @Size(max = 100) String filterfield,
        Object filtervalue,
        @NotNull OperatorEnum operatorType,
        FilterType filterType) {
}
