package com.onehostel.access.constants;

import com.onehostel.access.model.Privilege;
import com.onehostel.access.model.RolePrivileges;
import com.onehostel.access.model.User;
import com.onehostel.common.constants.EntityTypeSpec;
import com.onehostel.common.model.BaseDocument;

import java.util.Set;

/** Every enum of access-core, in one place. Import the nested type, e.g. {@code import com.onehostel.access.constants.AccessEnums.Role;}. */
public final class AccessEnums {

    private AccessEnums() {
    }

    /** Platform roles; the same names are Keycloak realm roles and appear in the token's "roles" claim. */
    public enum Role {
        SUPER_ADMIN,
        HOSTEL_OWNER,
        HOSTEL_MANAGER,
        HOSTEL_TENANT,
        SECURITY_GUARD;

        /** Roles that may only sign in through the mobile app, not the web portal. */
        public static final Set<Role> MOBILE_ONLY = Set.of(HOSTEL_TENANT, SECURITY_GUARD);
    }

    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        PENDING_VERIFICATION,
        BLOCKED
    }

    /** How a code is sent: by SMS to a mobile number, by email to an email address. */
    public enum OtpChannel {
        SMS,
        EMAIL
    }

    /** What a one-time code is for; a code issued for one purpose is never accepted for another. */
    public enum OtpPurpose {
        LOGIN,
        FORGOT_PASSWORD
    }

    /** Document types of access-core, for generic CRUD, messages and audit records. */
    public enum EntityType implements EntityTypeSpec {

        USER(User.class, "User"),
        PRIVILEGE(Privilege.class, "Privilege"),
        ROLE_PRIVILEGES(RolePrivileges.class, "Role privileges");

        private final Class<? extends BaseDocument> entityClass;
        private final String displayName;

        EntityType(Class<? extends BaseDocument> entityClass, String displayName) {
            this.entityClass = entityClass;
            this.displayName = displayName;
        }

        @Override
        public Class<? extends BaseDocument> entityClass() {
            return entityClass;
        }

        @Override
        public String displayName() {
            return displayName;
        }
    }
}
