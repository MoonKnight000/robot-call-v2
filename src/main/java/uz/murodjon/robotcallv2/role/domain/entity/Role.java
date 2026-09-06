package uz.murodjon.robotcallv2.role.domain.entity;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A named set of permissions inside one company (app_role). Two kinds exist and they
 * differ in where their permissions come from: a system role ({@link #code} set, e.g.
 * DEVELOPER) computes them from {@link SystemRole} on every read, so it can never drift
 * from the code; a company's own role stores them as rows and has no code.
 */
public record Role(
        long id,
        long companyId,
        String code,
        String name,
        String description,
        boolean system,
        Set<Permission> permissions,
        Instant createdAt
) {

    public Role {
        SystemRole systemRole = system ? SystemRole.findByCode(code) : null;
        permissions = systemRole != null ? systemRole.resolvePermissions() : immutableCopy(permissions);
    }

    /** A role the company defines itself — id, companyId and createdAt belong to the adapter. */
    public static Role custom(String name, String description, Set<Permission> permissions) {
        return new Role(0, 0, null, name, description, false, permissions, null);
    }

    /** One of the roles every company is seeded with; its permissions come from the enum. */
    public static Role ofSystemRole(SystemRole systemRole) {
        return new Role(0, 0, systemRole.name(), systemRole.label(), null, true, Set.of(), null);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /** The enum behind a system role, {@code null} for a company's own role. */
    public SystemRole findSystemRole() {
        return system ? SystemRole.findByCode(code) : null;
    }

    private static Set<Permission> immutableCopy(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }
}
