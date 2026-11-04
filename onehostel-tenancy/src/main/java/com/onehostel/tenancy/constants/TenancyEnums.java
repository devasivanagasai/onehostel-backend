package com.onehostel.tenancy.constants;

/** Every enum of the tenancy library, in one place. Import the nested type, e.g. {@code import com.onehostel.tenancy.constants.TenancyEnums.TenancyMode;}. */
public final class TenancyEnums {

    private TenancyEnums() {
    }

    public enum TenancyMode {
        /** No checks. */
        OFF,
        /** Operations run unchanged; accesses without a tenant filter are recorded as violations. */
        SHADOW,
        /** Every operation is restricted to the caller's tenant; an access without a tenant fails. */
        ENFORCE
    }
}
