package com.onehostel.service.helper;

import java.util.List;
import java.util.Set;

import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.dto.helper.HostelAbstractDto;
import com.onehostel.model.helper.HostelAbstractDocument;

/**
 * The contract every feature service fills in; HostelAbstractService supplies CRUD, tenancy,
 * paging and filtering on top of it.
 *
 * @param <E> the MongoDB document
 * @param <D> the create/update payload (DTO)
 * @param <V> what the API returns (view object); documents are never exposed directly
 */
public interface HostelAbstractInterface<E extends HostelAbstractDocument, D extends HostelAbstractDto, V> {

    /** Human name used in messages, e.g. "Hostel" in "Hostel created successfully." */
    String entityLabel();

    /** A new document from a create payload. Id, version and tenancy are set by the base service. */
    E dtoToEntity(D dto);

    /**
     * Copies the editable fields of an update payload onto the stored document. Must not
     * touch id, version, tenancy or audit fields.
     */
    void updateEntity(D dto, E entity);

    V entityToVO(E entity);

    /**
     * Business checks that Bean Validation cannot express, such as uniqueness. Raise an
     * ErrorCode on failure. {@code existing} is null on create.
     */
    default void validate(D dto, E existing, String organizationId) {
    }

    /** Fields a client may sort /getall by. Anything else falls back to the default sort. */
    default Set<String> sortableFields() {
        return Set.of("createdDateTime", "updatedDateTime");
    }

    /** Fields searched by ListFilter.keyword. Empty means keyword search is ignored. */
    default List<String> keywordFields() {
        return List.of();
    }

    /** Roles allowed to create, update and delete. Every role may read within its scope. */
    default Set<Role> writeRoles() {
        return Set.of(Role.SUPER_ADMIN, Role.HOSTEL_OWNER);
    }
}
