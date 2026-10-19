package com.onehostel.authgtwy.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.onehostel.common.constants.CommonEnumHelper.Permission;
import com.onehostel.common.constants.CommonEnumHelper.Role;

class EndpointPrivilegeTest {

    @Test
    void eachEndpointMapsToItsPermission() {
        assertThat(EndpointPrivilege.resolve("POST", "/api/v1/hostels/create")).contains(EndpointPrivilege.HOSTEL_CREATE);
        assertThat(EndpointPrivilege.resolve("POST", "/api/v1/hostels/getall")).contains(EndpointPrivilege.HOSTEL_READ);
        assertThat(EndpointPrivilege.resolve("POST", "/api/v1/organizations/delete"))
                .contains(EndpointPrivilege.ORGANIZATION_MANAGE);
        assertThat(EndpointPrivilege.resolve("GET", "/api/v1/auth/me")).contains(EndpointPrivilege.AUTH_ME);
        assertThat(EndpointPrivilege.resolve("DELETE", "/api/v1/admin/users/7")).contains(EndpointPrivilege.ADMIN_AREA);
    }

    @Test
    void unmappedEndpointsAndWrongMethodsResolveToNothing() {
        assertThat(EndpointPrivilege.resolve("POST", "/api/v1/rooms/create")).isEmpty();
        assertThat(EndpointPrivilege.resolve("GET", "/api/v1/hostels/create")).isEmpty();
        assertThat(EndpointPrivilege.resolve("POST", "/api/v1/auth/me")).isEmpty();
    }

    @Test
    void rolesHoldTheRightPermissions() {
        assertThat(Role.HOSTEL_TENANT.permissions()).contains(Permission.HOSTEL_READ)
                .doesNotContain(Permission.HOSTEL_CREATE, Permission.ORGANIZATION_MANAGE, Permission.GATEWAY_MANAGE);
        assertThat(Role.HOSTEL_OWNER.permissions()).contains(Permission.HOSTEL_CREATE, Permission.OWNER_AREA)
                .doesNotContain(Permission.ORGANIZATION_MANAGE, Permission.ADMIN_AREA);
        assertThat(Role.SUPER_ADMIN.permissions()).contains(Permission.ORGANIZATION_MANAGE, Permission.GATEWAY_MANAGE)
                .doesNotContain(Permission.OWNER_AREA, Permission.TENANT_AREA);
    }
}
