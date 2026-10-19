package com.onehostel.constants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.CommonEnumHelper.Status;
import com.onehostel.constants.EnumHelper.EnumOption;

class EnumHelperTest {

    @Test
    void parseIgnoresCaseAndSpaces() {
        assertThat(EnumHelper.parse(Role.class, " hostel_owner ")).contains(Role.HOSTEL_OWNER);
    }

    @Test
    void parseRejectsUnknownBlankAndNull() {
        assertThat(EnumHelper.parse(Role.class, "ADMIN")).isEqualTo(Optional.empty());
        assertThat(EnumHelper.isValid(Role.class, " ")).isFalse();
        assertThat(EnumHelper.isValid(Role.class, null)).isFalse();
    }

    @Test
    void labelsAreTitleCased() {
        assertThat(EnumHelper.toLabel(Role.HOSTEL_OWNER)).isEqualTo("Hostel Owner");
        assertThat(EnumHelper.toLabel(Status.ACTIVE)).isEqualTo("Active");
    }

    @Test
    void optionsKeepDeclarationOrder() {
        assertThat(CommonEnumHelper.STATUS_OPTIONS).startsWith(new EnumOption("Active", "ACTIVE"));
        assertThat(EnumHelper.names(Status.class)).containsExactly("ACTIVE", "INACTIVE", "PENDING", "ARCHIVED");
    }
}
