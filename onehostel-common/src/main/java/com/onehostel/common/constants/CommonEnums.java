package com.onehostel.common.constants;

/** Every enum shared by all services, in one place. Import the nested type, e.g. {@code import com.onehostel.common.constants.CommonEnums.ClientSource;}. */
public final class CommonEnums {

    private CommonEnums() {
    }

    /** Platform a request comes from (clientSource in the request envelope). */
    public enum ClientSource {
        WEB,
        MOBILE_ANDROID,
        MOBILE_IOS
    }

    public enum SortOrder {
        ASC,
        DESC
    }

    public enum FilterType {
        /** The value is used as typed for the field (default). */
        DEFAULT,
        /** A day or a range, "2026-01-01" or "2026-01-01 to 2026-01-31", both ends inclusive. */
        DATE
    }

    public enum OperatorEnum {
        EQUAL,
        NOT_EQUAL,
        LIKE,
        IN,
        NOT_IN,
        GREATER_THAN,
        GREATER_THAN_EQUAL,
        LESS_THAN,
        LESS_THAN_EQUAL,
        EQUAL_OR_NULL,
        LIKE_OR_NULL
    }

    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE,
        APPROVE,
        REJECT,
        LOGIN,
        LOGOUT,
        PASSWORD_CHANGE,
        EXPORT,
        OTHER
    }

    public enum AndOrType {
        AND,
        OR
    }

    public enum Change {
        CREATED,
        UPDATED,
        DELETED
    }
}
