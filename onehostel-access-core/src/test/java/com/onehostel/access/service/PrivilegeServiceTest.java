package com.onehostel.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.helper.PrivilegeCatalog;
import com.onehostel.access.model.Privilege;
import com.onehostel.access.model.RolePrivileges;
import com.onehostel.access.repository.PrivilegeRepository;
import com.onehostel.access.repository.RolePrivilegesRepository;
import com.onehostel.common.exception.HostelException;
import com.onehostel.common.helper.PrivTokens;
import com.onehostel.common.service.AuditRecorder;

class PrivilegeServiceTest {

    /** In-memory stand-ins for the two collections. */
    private final List<Privilege> privilegeRows = new ArrayList<>();
    private final List<RolePrivileges> grantRows = new ArrayList<>();
    private PrivilegeService service;

    @BeforeEach
    void setUp() {
        PrivilegeRepository privileges = mock(PrivilegeRepository.class);
        when(privileges.findAll()).thenAnswer(i -> List.copyOf(privilegeRows));
        when(privileges.findByCode(anyString())).thenAnswer(i -> privilegeRows.stream()
                .filter(p -> p.getCode().equals(i.getArgument(0))).findFirst());
        when(privileges.findTopByOrderByBitDesc()).thenAnswer(i -> privilegeRows.stream()
                .max(Comparator.comparing(Privilege::getBit)));
        when(privileges.insert(any(Privilege.class))).thenAnswer(i -> {
            privilegeRows.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(privileges.save(any(Privilege.class))).thenAnswer(i -> i.getArgument(0));

        RolePrivilegesRepository grants = mock(RolePrivilegesRepository.class);
        when(grants.findAll()).thenAnswer(i -> List.copyOf(grantRows));
        when(grants.findByRole(any())).thenAnswer(i -> grantRows.stream()
                .filter(g -> g.getRole() == i.getArgument(0)).findFirst());
        when(grants.findByRoleIn(anyCollection())).thenAnswer(i -> grantRows.stream()
                .filter(g -> ((Collection<?>) i.getArgument(0)).contains(g.getRole())).toList());
        when(grants.save(any(RolePrivileges.class))).thenAnswer(i -> {
            RolePrivileges row = i.getArgument(0);
            if (!grantRows.contains(row)) {
                grantRows.add(row);
            }
            return row;
        });

        service = new PrivilegeService(privileges, grants, mock(AuditRecorder.class), new ObjectMapper());
    }

    private Privilege privilege(String code) {
        return privilegeRows.stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow();
    }

    private RolePrivileges grants(Role role) {
        return grantRows.stream().filter(g -> g.getRole() == role).findFirst().orElseThrow();
    }

    private static Privilege stored(String code, int bit) {
        Privilege privilege = new Privilege();
        privilege.setCode(code);
        privilege.setBit(bit);
        return privilege;
    }

    @Test
    void firstSyncNumbersEveryPrivilegeFromOneInCatalogueOrder() {
        service.syncCatalog();

        List<Integer> bits = PrivilegeCatalog.definitions().stream().map(d -> privilege(d.code()).getBit()).toList();
        assertThat(bits).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, PrivilegeCatalog.definitions().size()).boxed().toList());
    }

    @Test
    void existingBitsAreKeptAndNewPrivilegesGoAfterTheHighestEverTaken() {
        privilegeRows.add(stored("ROOMS_VIEW", 7));
        privilegeRows.add(stored("SOMETHING_REMOVED", 40));

        service.syncCatalog();

        assertThat(privilege("ROOMS_VIEW").getBit()).isEqualTo(7);
        assertThat(privilege("SOMETHING_REMOVED").isRetired()).isTrue();
        assertThat(privilege("HOSTEL_ONBOARDING").getBit()).isEqualTo(41);
        Set<Integer> bits = privilegeRows.stream().map(Privilege::getBit).collect(Collectors.toSet());
        assertThat(bits).hasSize(privilegeRows.size());
    }

    @Test
    void defaultsMirrorThePortalRules() {
        service.syncCatalog();

        assertThat(grants(Role.SUPER_ADMIN).getPrivilegeCodes()).contains("HOSTEL_ONBOARDING", "ROLE_MANAGE").doesNotContain("ROOMS_VIEW");
        assertThat(grants(Role.HOSTEL_OWNER).getPrivilegeCodes()).contains("ROOMS_VIEW", "ROOMS_MANAGE", "OWNER_DASHBOARD")
                .doesNotContain("MANAGER_OVERVIEW", "HOSTEL_ONBOARDING");
        assertThat(grants(Role.HOSTEL_MANAGER).getPrivilegeCodes()).contains("ROOMS_VIEW", "MANAGER_OVERVIEW")
                .doesNotContain("OWNER_DASHBOARD");
        assertThat(grantRows).noneMatch(g -> g.getRole() == Role.HOSTEL_TENANT || g.getRole() == Role.SECURITY_GUARD);
    }

    @Test
    void aDefaultAnAdminRemovedIsNotGrantedBackAtTheNextStartup() {
        service.syncCatalog();
        grants(Role.HOSTEL_MANAGER).getPrivilegeCodes().remove("FEES_MANAGE");

        service.syncCatalog();

        assertThat(grants(Role.HOSTEL_MANAGER).getPrivilegeCodes()).doesNotContain("FEES_MANAGE");
    }

    @Test
    void tokenHasExactlyTheRolesBits() {
        service.syncCatalog();

        String token = service.privTokenFor(List.of("HOSTEL_OWNER", "offline_access"));

        assertThat(PrivTokens.hasBit(token, privilege("ROOMS_MANAGE").getBit())).isTrue();
        assertThat(PrivTokens.hasBit(token, privilege("OWNER_DASHBOARD").getBit())).isTrue();
        assertThat(PrivTokens.hasBit(token, privilege("HOSTEL_ONBOARDING").getBit())).isFalse();
        assertThat(PrivTokens.hasBit(token, privilege("MANAGER_OVERVIEW").getBit())).isFalse();
        assertThat(token).hasSize(privilegeRows.size());
    }

    @Test
    void retiredPrivilegesAreNeverInAToken() {
        service.syncCatalog();
        privilege("ROOMS_VIEW").setRetired(true);

        String token = service.privTokenFor(List.of("HOSTEL_OWNER"));

        assertThat(PrivTokens.hasBit(token, privilege("ROOMS_VIEW").getBit())).isFalse();
    }

    @Test
    void unknownRolesGetAnAllZeroToken() {
        service.syncCatalog();

        assertThat(service.privTokenFor(List.of("default-roles-onehostel"))).doesNotContain("1");
    }

    @Test
    void readsTheRolesOfAnAccessToken() {
        service.syncCatalog();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"roles\":[\"SUPER_ADMIN\"]}".getBytes(StandardCharsets.UTF_8));

        String token = service.privTokenForAccessToken("header." + payload + ".signature");

        assertThat(PrivTokens.hasBit(token, privilege("ROLE_MANAGE").getBit())).isTrue();
        assertThat(service.privTokenForAccessToken("not-a-jwt")).doesNotContain("1");
    }

    @Test
    void unknownPrivilegeCannotBeGranted() {
        service.syncCatalog();

        assertThatThrownBy(() -> service.updateRoleGrants(Role.HOSTEL_MANAGER, Set.of("ROOMS_VIEW", "NOPE")))
                .extracting(e -> ((HostelException) e).getErrorCode()).isEqualTo(ErrorCode.UNKNOWN_PRIVILEGE);
    }

    @Test
    void superAdminKeepsRoleManage() {
        service.syncCatalog();

        assertThatThrownBy(() -> service.updateRoleGrants(Role.SUPER_ADMIN, Set.of("AUDIT_LOGS")))
                .extracting(e -> ((HostelException) e).getErrorCode()).isEqualTo(ErrorCode.ROLE_MANAGE_REQUIRED);
    }

    @Test
    void updatedGrantsShowInTheMatrix() {
        service.syncCatalog();

        service.updateRoleGrants(Role.HOSTEL_MANAGER, Set.of("ROOMS_VIEW"));

        String managerToken = service.matrix().roleTokens().get("HOSTEL_MANAGER");
        assertThat(PrivTokens.hasBit(managerToken, privilege("ROOMS_VIEW").getBit())).isTrue();
        assertThat(PrivTokens.hasBit(managerToken, privilege("ROOMS_MANAGE").getBit())).isFalse();
        assertThat(Optional.ofNullable(service.matrix().roleTokens().get("HOSTEL_TENANT"))).hasValueSatisfying(t -> assertThat(t).doesNotContain("1"));
    }
}
