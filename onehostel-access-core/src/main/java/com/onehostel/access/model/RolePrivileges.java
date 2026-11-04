package com.onehostel.access.model;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.constants.CollectionNames;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.tenancy.annotation.GlobalCollection;

import lombok.Getter;
import lombok.Setter;

/** The privileges a role holds, by code. One document per role. */
@Getter
@Setter
@Document(CollectionNames.ROLE_PRIVILEGES)
@GlobalCollection(reason = "Roles are platform-wide; per-organization custom roles would add an organizationId scope")
public class RolePrivileges extends BaseDocument {

    @Indexed(unique = true)
    private Role role;

    private Set<String> privilegeCodes = new HashSet<>();

    /**
     * Every catalogue default ever applied to this role. A default is applied only once, so a privilege an
     * admin later removes is not granted back at the next startup.
     */
    private Set<String> seededCodes = new HashSet<>();
}
