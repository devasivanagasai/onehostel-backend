package com.onehostel.access.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.constants.AccessEnums.UserStatus;
import com.onehostel.access.constants.CollectionNames;
import com.onehostel.access.vo.AccessScope;
import com.onehostel.access.vo.MobileNumber;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.tenancy.annotation.GlobalCollection;

import lombok.Getter;
import lombok.Setter;

/**
 * A OneHostel account. The password lives in Keycloak (keycloakUserId); this record holds who the user is
 * and where they may act. organizationId (from BaseDocument) is the primary scope's organization.
 */
@Getter
@Setter
@Document(CollectionNames.USERS)
@GlobalCollection(reason = "Looked up by email or mobile at login, before a tenant is known; a user can belong to several organizations")
public class User extends BaseDocument {

    @Indexed(unique = true, sparse = true)
    private String username;

    @Indexed(unique = true, sparse = true)
    private String email;

    private MobileNumber mobileNumber;

    @Indexed(sparse = true)
    private String keycloakUserId;

    private String firstName;

    private String lastName;

    private UserStatus status;

    private boolean locked;

    /** Where the user acts by default; its role decides web vs mobile sign-in. */
    private AccessScope primaryAccess;

    private List<AccessScope> accessScopes;

    private Instant passwordChangedAt;

    public Role primaryRole() {
        return primaryAccess != null ? primaryAccess.role() : null;
    }

    /** The username Keycloak knows the user by (accounts are created with the username, else the email). */
    public String keycloakUsername() {
        return username != null ? username : email;
    }
}
