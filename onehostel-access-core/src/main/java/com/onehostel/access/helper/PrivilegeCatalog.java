package com.onehostel.access.helper;

import static com.onehostel.access.constants.AccessEnums.Role.HOSTEL_MANAGER;
import static com.onehostel.access.constants.AccessEnums.Role.HOSTEL_OWNER;
import static com.onehostel.access.constants.AccessEnums.Role.SUPER_ADMIN;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.vo.ApiRule;
import com.onehostel.access.vo.PrivilegeDefinition;

/**
 * Every privilege, declared once. PrivilegeService syncs this list into the privileges collection at startup
 * and assigns each new code the next free bit. To add one, append a line; to drop one, delete it (its bit is
 * retired, never reused). Never rename a code: grants and the Angular app refer to it.
 *
 * <p>Defaults mirror the web portal's rules: platform pages for SUPER_ADMIN, hostel modules for the Hostel
 * Owner and Hostel Manager. Tenants and security guards use the mobile app and hold no web privileges yet.
 */
public final class PrivilegeCatalog {

    public static final String ROLE_MANAGE = "ROLE_MANAGE";

    private static final Set<Role> ADMIN = Set.of(SUPER_ADMIN);
    private static final Set<Role> HOSTEL_STAFF = Set.of(HOSTEL_OWNER, HOSTEL_MANAGER);

    private static final List<PrivilegeDefinition> DEFINITIONS = build();

    private PrivilegeCatalog() {
    }

    public static List<PrivilegeDefinition> definitions() {
        return DEFINITIONS;
    }

    private static List<PrivilegeDefinition> build() {
        List<PrivilegeDefinition> all = new ArrayList<>();

        // Platform administration (SUPER_ADMIN)
        all.add(page("HOSTEL_ONBOARDING", "Hostel onboarding", "Platform", ADMIN, "/admin", "/hostel-onboarding", "/hostels", "/institution"));
        all.add(page("SUBSCRIPTION_PLANS", "Subscription plans", "Platform", ADMIN, "/subscription-plans"));
        all.add(page("SUBSCRIPTION_PAYMENTS", "Subscription payments", "Platform", ADMIN, "/subscription-payments"));
        all.add(page("COMMUNICATION_TEMPLATES", "Communication templates", "Platform", ADMIN, "/communication-templates"));
        all.add(page("ADMIN_ACCOUNTS", "Admin accounts", "Platform", ADMIN, "/admins"));
        all.add(new PrivilegeDefinition("USER_MANAGEMENT", "User management", "Platform", List.of("/users"),
                List.of(ApiRule.any("/api/v1/access/users/**")), ADMIN));
        all.add(page("PLATFORM_REPORTS", "Platform reports", "Platform", ADMIN, "/reports"));
        all.add(page("AUDIT_LOGS", "Audit logs", "Platform", ADMIN, "/audit-logs"));
        all.add(page("SYSTEM_SETTINGS", "System settings", "Platform", ADMIN, "/settings"));
        all.add(new PrivilegeDefinition(ROLE_MANAGE, "Manage roles and privileges", "Platform", List.of(),
                List.of(ApiRule.any("/api/v1/access/roles"), ApiRule.any("/api/v1/access/roles/**")), ADMIN));

        // Landing pages of one hostel role each
        all.add(page("OWNER_DASHBOARD", "Owner dashboard", "Dashboard", Set.of(HOSTEL_OWNER), "/owner"));
        all.add(page("MANAGER_OVERVIEW", "Branch overview", "Dashboard", Set.of(HOSTEL_MANAGER), "/manager"));

        // Hostel modules (Hostel Owner, Hostel Manager): VIEW opens the page and reads, MANAGE changes data
        hostelModule(all, "ROOMS", "Rooms", "/rooms", "/api/v1/property");
        hostelModule(all, "RESIDENTS", "Residents", "/residents", "/api/v1/resident");
        hostelModule(all, "FEES", "Fees & payments", "/fees", "/api/v1/billing");
        hostelModule(all, "COMPLAINTS", "Complaints", "/complaints", null);
        hostelModule(all, "LAUNDRY", "Laundry", "/laundry", null);
        hostelModule(all, "VISITORS", "Visitors", "/visitors", null);
        hostelModule(all, "MESS", "Mess", "/mess", null);
        hostelModule(all, "LEAVE_OUTPASS", "Leave & outpass", "/leave-outpass", null);
        hostelModule(all, "ATTENDANCE", "Attendance", "/attendance", null);
        hostelModule(all, "PARCELS", "Parcels", "/parcels", null);
        hostelModule(all, "ANNOUNCEMENTS", "Announcements", "/announcements", null);
        hostelModule(all, "HOSTEL_REPORTS", "Hostel reports", "/hostel-reports", null);
        hostelModule(all, "HOSTEL_SETTINGS", "Hostel settings", "/hostel-settings", null);
        return List.copyOf(all);
    }

    private static PrivilegeDefinition page(String code, String name, String module, Set<Role> roles, String... routes) {
        return new PrivilegeDefinition(code, name, module, List.of(routes), List.of(), roles);
    }

    /**
     * {CODE}_VIEW and {CODE}_MANAGE for a hostel module. With an API prefix (the service behind the module),
     * VIEW allows the generic CRUD reads (GET, POST .../getall) and MANAGE every call under the prefix.
     */
    private static void hostelModule(List<PrivilegeDefinition> all, String code, String name, String route, String apiPrefix) {
        List<ApiRule> read = apiPrefix == null ? List.of()
                : List.of(ApiRule.of("GET", apiPrefix + "/**"), ApiRule.of("POST", apiPrefix + "/**/getall"));
        List<ApiRule> write = apiPrefix == null ? List.of() : List.of(ApiRule.any(apiPrefix + "/**"));
        all.add(new PrivilegeDefinition(code + "_VIEW", "View " + name.toLowerCase(), name, List.of(route), read, HOSTEL_STAFF));
        all.add(new PrivilegeDefinition(code + "_MANAGE", "Manage " + name.toLowerCase(), name, List.of(), write, HOSTEL_STAFF));
    }
}
