package com.onehostel.access.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.access.constants.AccessEnums.EntityType;
import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.dto.PrivilegeDtos.MatrixEntry;
import com.onehostel.access.dto.PrivilegeDtos.PrivilegeMatrixResponse;
import com.onehostel.access.dto.PrivilegeDtos.PrivilegeResponse;
import com.onehostel.access.dto.PrivilegeDtos.RoleGrantsResponse;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.helper.PrivilegeCatalog;
import com.onehostel.access.model.Privilege;
import com.onehostel.access.model.RolePrivileges;
import com.onehostel.access.repository.PrivilegeRepository;
import com.onehostel.access.repository.RolePrivilegesRepository;
import com.onehostel.access.vo.PrivilegeDefinition;
import com.onehostel.common.constants.CommonEnums.AuditAction;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.helper.PrivTokens;
import com.onehostel.common.service.AuditRecorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The privilege registry and role grants. Each privilege owns one bit of the privToken; bits are handed out
 * once, in registration order, and never reused, so they are generated rather than hand-edited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivilegeService {

    /** Claim holding the realm roles as a flat list (protocol mapper "realm-roles-flat"); the gateway reads the same. */
    private static final String ROLES_CLAIM = "roles";
    private static final int MAX_BIT_ATTEMPTS = 5;

    private final PrivilegeRepository privileges;
    private final RolePrivilegesRepository roleGrants;
    private final AuditRecorder audit;
    private final ObjectMapper objectMapper;

    /**
     * Brings the privileges collection and the role defaults in line with PrivilegeCatalog. Runs at every
     * startup and is safe on several replicas at once: the unique indexes on code and bit settle any race.
     */
    public void syncCatalog() {
        List<PrivilegeDefinition> definitions = PrivilegeCatalog.definitions();
        definitions.forEach(this::register);

        Set<String> declared = definitions.stream().map(PrivilegeDefinition::code).collect(Collectors.toSet());
        for (Privilege privilege : privileges.findAll()) {
            boolean retired = !declared.contains(privilege.getCode());
            if (privilege.isRetired() != retired) {
                privilege.setRetired(retired);
                saveQuietly(privilege);
                log.info("Privilege {} (bit {}) {}", privilege.getCode(), privilege.getBit(), retired ? "retired" : "restored");
            }
        }
        definitions.stream().flatMap(d -> d.defaultRoles().stream()).distinct().forEach(role -> seedDefaults(role, definitions));
    }

    /** The privToken of whatever roles a Keycloak access token carries. An unreadable token holds nothing. */
    public String privTokenForAccessToken(String accessToken) {
        return privTokenFor(rolesIn(accessToken));
    }

    /** Union of the roles' grants, one position per bit; unknown role names are ignored. */
    public String privTokenFor(Collection<String> roleNames) {
        Set<Role> roles = roleNames.stream().map(PrivilegeService::toRole).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Privilege> all = privileges.findAll();
        Map<String, Privilege> active = activeByCode(all);
        Set<Integer> bits = roles.isEmpty() ? Set.of() : roleGrants.findByRoleIn(roles).stream()
                .flatMap(grants -> grants.getPrivilegeCodes().stream())
                .map(active::get).filter(Objects::nonNull).map(Privilege::getBit)
                .collect(Collectors.toSet());
        return PrivTokens.of(bits, tokenLength(all));
    }

    /** Active privileges in bit order, for the Angular app. */
    public List<PrivilegeResponse> catalog() {
        return privileges.findAll().stream().filter(p -> !p.isRetired())
                .sorted(Comparator.comparing(Privilege::getBit))
                .map(p -> new PrivilegeResponse(p.getCode(), p.getBit(), p.getName(), p.getModule(), p.getUiRoutes()))
                .toList();
    }

    /** For the gateway: the APIs behind each bit, and every role's token. */
    public PrivilegeMatrixResponse matrix() {
        List<Privilege> all = privileges.findAll();
        Map<String, Privilege> active = activeByCode(all);
        int length = tokenLength(all);
        Map<String, String> roleTokens = new LinkedHashMap<>();
        Map<Role, RolePrivileges> grantsByRole = grantsByRole();
        for (Role role : Role.values()) {
            RolePrivileges grants = grantsByRole.get(role);
            Set<Integer> bits = grants == null ? Set.of() : grants.getPrivilegeCodes().stream()
                    .map(active::get).filter(Objects::nonNull).map(Privilege::getBit).collect(Collectors.toSet());
            roleTokens.put(role.name(), PrivTokens.of(bits, length));
        }
        List<MatrixEntry> entries = active.values().stream().sorted(Comparator.comparing(Privilege::getBit))
                .map(p -> new MatrixEntry(p.getCode(), p.getBit(), p.getApis() == null ? List.of() : p.getApis()))
                .toList();
        return new PrivilegeMatrixResponse(entries, roleTokens);
    }

    public List<RoleGrantsResponse> roles() {
        Map<Role, RolePrivileges> grantsByRole = grantsByRole();
        Set<String> active = activeByCode(privileges.findAll()).keySet();
        List<RoleGrantsResponse> roles = new ArrayList<>();
        for (Role role : Role.values()) {
            RolePrivileges grants = grantsByRole.get(role);
            Set<String> codes = grants == null ? Set.of() : grants.getPrivilegeCodes().stream()
                    .filter(active::contains).collect(Collectors.toCollection(TreeSet::new));
            roles.add(new RoleGrantsResponse(role, codes));
        }
        return roles;
    }

    /**
     * Replaces a role's privileges. Signed-in users pick the change up at their next token refresh; the
     * gateway within its matrix cache time.
     */
    public RoleGrantsResponse updateRoleGrants(Role role, Set<String> codes) {
        Set<String> active = activeByCode(privileges.findAll()).keySet();
        codes.stream().filter(code -> !active.contains(code)).findFirst().ifPresent(code -> {
            throw ErrorCode.UNKNOWN_PRIVILEGE.raise(code);
        });
        if (role == Role.SUPER_ADMIN && !codes.contains(PrivilegeCatalog.ROLE_MANAGE)) {
            throw ErrorCode.ROLE_MANAGE_REQUIRED.raise(PrivilegeCatalog.ROLE_MANAGE);
        }
        RolePrivileges grants = roleGrants.findByRole(role).orElseGet(() -> newGrants(role));
        Set<String> added = new TreeSet<>(codes);
        added.removeAll(grants.getPrivilegeCodes());
        Set<String> removed = new TreeSet<>(grants.getPrivilegeCodes());
        removed.removeAll(codes);

        grants.setPrivilegeCodes(new HashSet<>(codes));
        RolePrivileges saved = roleGrants.save(grants);
        audit.record(AuditAction.UPDATE, EntityType.ROLE_PRIVILEGES.name(), saved.getId(), null,
                role + " granted " + added + ", removed " + removed);
        return new RoleGrantsResponse(role, new TreeSet<>(codes));
    }

    private void register(PrivilegeDefinition definition) {
        Privilege existing = privileges.findByCode(definition.code()).orElse(null);
        if (existing == null) {
            insertWithNextBit(definition);
        } else if (apply(definition, existing)) {
            saveQuietly(existing);
        }
    }

    private void insertWithNextBit(PrivilegeDefinition definition) {
        for (int attempt = 0; attempt < MAX_BIT_ATTEMPTS; attempt++) {
            int bit = privileges.findTopByOrderByBitDesc().map(Privilege::getBit).orElse(0) + 1;
            Privilege privilege = new Privilege();
            privilege.setCode(definition.code());
            privilege.setBit(bit);
            apply(definition, privilege);
            try {
                privileges.insert(privilege);
                log.info("Registered privilege {} at bit {}", definition.code(), bit);
                return;
            } catch (DuplicateKeyException e) {
                // Another replica registered a privilege at the same moment: done if it was this one, else retry with the next bit
                if (privileges.findByCode(definition.code()).isPresent()) {
                    return;
                }
            }
        }
        throw CommonErrorCode.STATE_CONFLICT.raise("Could not assign a bit to privilege " + definition.code());
    }

    /** Copies the declared fields; true when anything changed. */
    private static boolean apply(PrivilegeDefinition definition, Privilege privilege) {
        boolean changed = !Objects.equals(privilege.getName(), definition.name())
                || !Objects.equals(privilege.getModule(), definition.module())
                || !Objects.equals(privilege.getUiRoutes(), definition.uiRoutes())
                || !Objects.equals(privilege.getApis(), definition.apis());
        privilege.setName(definition.name());
        privilege.setModule(definition.module());
        privilege.setUiRoutes(definition.uiRoutes());
        privilege.setApis(definition.apis());
        return changed;
    }

    /** Grants each catalogue default once; a default an admin later removed stays removed. */
    private void seedDefaults(Role role, List<PrivilegeDefinition> definitions) {
        RolePrivileges grants = roleGrants.findByRole(role).orElseGet(() -> newGrants(role));
        Set<String> fresh = definitions.stream().filter(d -> d.defaultRoles().contains(role))
                .map(PrivilegeDefinition::code).filter(code -> !grants.getSeededCodes().contains(code))
                .collect(Collectors.toCollection(TreeSet::new));
        if (fresh.isEmpty()) {
            return;
        }
        grants.getPrivilegeCodes().addAll(fresh);
        grants.getSeededCodes().addAll(fresh);
        try {
            roleGrants.save(grants);
            log.info("Granted {} the default privileges {}", role, fresh);
        } catch (DuplicateKeyException | OptimisticLockingFailureException e) {
            // Another replica seeded the same role at the same moment; its write carries the same defaults
            log.info("Defaults of {} were seeded by another instance", role);
        }
    }

    private void saveQuietly(Privilege privilege) {
        try {
            privileges.save(privilege);
        } catch (OptimisticLockingFailureException e) {
            // Another replica synced the same catalogue change at the same moment; both write the same values
            log.info("Privilege {} was synced by another instance", privilege.getCode());
        }
    }

    private Map<Role, RolePrivileges> grantsByRole() {
        Map<Role, RolePrivileges> byRole = new EnumMap<>(Role.class);
        roleGrants.findAll().forEach(grants -> byRole.put(grants.getRole(), grants));
        return byRole;
    }

    private static RolePrivileges newGrants(Role role) {
        RolePrivileges grants = new RolePrivileges();
        grants.setRole(role);
        return grants;
    }

    private static Map<String, Privilege> activeByCode(List<Privilege> all) {
        return all.stream().filter(p -> !p.isRetired())
                .collect(Collectors.toMap(Privilege::getCode, Function.identity(), (a, b) -> a));
    }

    /** Covers every bit ever assigned, retired ones included, so positions never shift. */
    private static int tokenLength(List<Privilege> all) {
        return all.stream().map(Privilege::getBit).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
    }

    private static Role toRole(String name) {
        try {
            return name == null ? null : Role.valueOf(name);
        } catch (IllegalArgumentException e) {
            // Keycloak also puts its own roles (offline_access, default-roles-*) in the claim
            return null;
        }
    }

    /**
     * The roles claim of a token Keycloak just issued to this service over its back channel, so the signature
     * is not re-checked here (the gateway verifies every token it is later sent).
     */
    private List<String> rolesIn(String accessToken) {
        String[] parts = accessToken == null ? new String[0] : accessToken.split("\\.");
        if (parts.length < 2) {
            return List.of();
        }
        try {
            JsonNode roles = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1])).path(ROLES_CLAIM);
            List<String> names = new ArrayList<>();
            roles.forEach(role -> names.add(role.asText()));
            return names;
        } catch (IOException | IllegalArgumentException e) {
            // An unreadable token grants nothing: the user signs in with an empty privToken
            log.warn("Could not read the roles of an access token", e);
            return List.of();
        }
    }
}
