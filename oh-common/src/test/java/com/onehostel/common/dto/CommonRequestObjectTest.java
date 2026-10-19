package com.onehostel.dto.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.onehostel.constants.CommonEnumHelper.SortOrder;

class CommonRequestObjectTest {

    private static final Set<String> SORTABLE = Set.of("name", "createdDateTime");

    @Test
    void missingPagingUsesProjectDefaults() {
        Pageable pageable = CommonRequestObject.of("payload").toPageable(SORTABLE);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdDateTime"));
    }

    @Test
    void requestedPageAndSortAreUsed() {
        Pageable pageable = paged("name", SortOrder.ASC, null).toPageable(SORTABLE);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    void sortingByAFieldOutsideTheAllowListFallsBackToDefault() {
        Pageable pageable = paged("passwordHash", SortOrder.ASC, null).toPageable(SORTABLE);

        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "createdDateTime"));
    }

    @Test
    void paginationCanBeTurnedOff() {
        assertThat(paged("name", null, false).toPageable(SORTABLE).isPaged()).isFalse();
        assertThat(CommonRequestObject.of("payload").isPaginationRequired()).isTrue();
    }

    private static CommonRequestObject<String> paged(String sortField, SortOrder order, Boolean paginationRequired) {
        return new CommonRequestObject<>("payload", null, null, null, null, null, 2, 25, sortField, order,
                paginationRequired);
    }
}
