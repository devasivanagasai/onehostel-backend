package com.onehostel.core.constants;

public final class EnumHelper {

    private EnumHelper() {
        // Utility class, prevent instantiation
    }

    /**
     * User roles across the OneHostel platform.
     */
    public enum Role {
        SUPER_ADMIN,
        HOSTEL_OWNER,
        HOSTEL_MANAGER,
        HOSTEL_TENANT,
        SECURITY_GUARD
    }

    /**
     * Account lifecycle statuses.
     */
    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        PENDING_VERIFICATION,
        BLOCKED
    }

    /**
     * Authentication modes supported during login.
     */
    public enum AuthMode {
        PASSWORD,
        PASSCODE,
        OTP
    }

    /**
     * Client platform initiating the request.
     */
    public enum ClientSource {
        WEB,
        MOBILE_ANDROID,
        MOBILE_IOS
    }

    /**
     * Communication delivery channels for OTP challenges and alerts.
     */
    public enum OtpChannel {
        SMS,
        EMAIL
    }

    /**
     * What an OTP challenge may be used for; a code issued for one purpose is never accepted for another.
     */
    public enum OtpPurpose {
        LOGIN,
        FORGOT_PASSWORD
    }

    /**
     * Sorting order for paginated list queries.
     */
    public enum SortOrder {
        ASC,
        DESC
    }

    /**
     * Logical filter operations for query builders.
     */
    public enum OperatorEnum {
        EQUAL,
        LIKE,
        NOT_EQUAL,
        IN,
        NOT_IN,
        GREATER_THAN,
        LESS_THAN,
        GREATER_THAN_EQUAL,
        LESS_THAN_EQUAL,
        EQUAL_OR_NULL,
        LIKE_OR_NULL
    }

    /**
     * Entity types supported by the polymorphic search mirror and generic CRUD.
     */
    public enum EntityTypeEnum {
        USER,
        HOSTEL,
        ROOM,
        BED,
        RESIDENT,
        INVOICE,
        PAYMENT,
        COMPLAINT,
        VISITOR_LOG,
        GATE_PASS,
        AUDIT_LOG
    }
}