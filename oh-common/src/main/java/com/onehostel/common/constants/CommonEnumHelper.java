package com.onehostel.common.constants;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;

import com.onehostel.common.constants.EnumHelper.EnumOption;

/**
 * Enums shared across features, and their ready-made dropdown options.
 *
 * Presentation details such as badge colours belong to the Angular app; the backend only
 * supplies values and labels.
 */
public final class CommonEnumHelper {

    /**
     * The three kinds of account. Each maps to the Spring Security authority {@code ROLE_<name>}.
     * The name is stored on users and inside JWTs: renaming a constant needs a data migration
     * (see RoleMigration).
     */
    public enum Role {
        /** Platform operator: every organization. */
        SUPER_ADMIN(EnumSet.of(Permission.ACCOUNT_SELF, Permission.ADMIN_AREA, Permission.GATEWAY_MANAGE,
                Permission.ORGANIZATION_READ, Permission.ORGANIZATION_MANAGE,
                Permission.HOSTEL_READ, Permission.HOSTEL_CREATE, Permission.HOSTEL_UPDATE, Permission.HOSTEL_DELETE)),
        /** Runs one property group (organization) of hostels. */
        HOSTEL_OWNER(EnumSet.of(Permission.ACCOUNT_SELF, Permission.OWNER_AREA, Permission.ORGANIZATION_READ,
                Permission.HOSTEL_READ, Permission.HOSTEL_CREATE, Permission.HOSTEL_UPDATE, Permission.HOSTEL_DELETE)),
        /** Resident of a hostel. */
        HOSTEL_TENANT(EnumSet.of(Permission.ACCOUNT_SELF, Permission.TENANT_AREA, Permission.ORGANIZATION_READ,
                Permission.HOSTEL_READ));

        private final Set<Permission> permissions;

        Role(Set<Permission> permissions) {
            this.permissions = permissions;
        }

        public String authority() {
            return "ROLE_" + name();
        }

        /** Action codes for the UI to show or hide features. The server enforces access itself. */
        public Set<Permission> permissions() {
            return permissions;
        }
    }

    /**
     * What a role may do. The gateway maps every protected endpoint to one of these
     * (EndpointPrivilege) and refuses the request when the caller's role lacks it.
     */
    public enum Permission {
        /** The signed-in user's own account: profile, password, sessions. */
        ACCOUNT_SELF,
        ADMIN_AREA,
        OWNER_AREA,
        TENANT_AREA,
        /** Gateway administration, e.g. the public-endpoint list. */
        GATEWAY_MANAGE,
        ORGANIZATION_READ,
        ORGANIZATION_MANAGE,
        HOSTEL_READ,
        HOSTEL_CREATE,
        HOSTEL_UPDATE,
        HOSTEL_DELETE
    }

    /**
     * How a login proves who the user is. PASSCODE is for super admins only; OTP for owners and
     * tenants only (the code from /otp/send).
     */
    public enum AuthMode {
        PASSWORD,
        PASSCODE,
        OTP
    }

    /** Account lifecycle. Only ACTIVE accounts that are not locked can log in. */
    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        PENDING_VERIFICATION,
        BLOCKED
    }

    /** What a HostelCommonSearch row mirrors. */
    public enum EntityType {
        USER,
        HOSTEL
    }

    public enum OtpChannel {
        SMS,
        EMAIL
    }

    /** Lifecycle of a record (hostel, room, booking, ...). */
    public enum Status {
        ACTIVE,
        INACTIVE,
        PENDING,
        ARCHIVED
    }

    public enum Gender {
        MALE,
        FEMALE,
        OTHER
    }

    /** Which app sent a request (CommonRequestObject.clientSource). */
    public enum ClientSource {
        WEB,
        MOBILE_ANDROID,
        MOBILE_IOS
    }

    public enum SortOrder {
        ASC,
        DESC;

        public Sort.Direction toDirection() {
            return this == ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
    }

    public static final List<EnumOption> ROLE_OPTIONS = EnumHelper.options(Role.class);
    public static final List<EnumOption> STATUS_OPTIONS = EnumHelper.options(Status.class);
    public static final List<EnumOption> GENDER_OPTIONS = EnumHelper.options(Gender.class);

    private CommonEnumHelper() {
    }
}
