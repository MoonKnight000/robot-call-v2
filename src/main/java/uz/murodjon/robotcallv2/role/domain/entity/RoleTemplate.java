package uz.murodjon.robotcallv2.role.domain.entity;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A platform-wide default role (role_template). Every new company receives a copy of each
 * template as one of its own roles; editing a template later does not touch existing companies.
 * {@link #code} survives into the copy, which is how code still finds "the ADMIN role".
 */
public record RoleTemplate(
        long id,
        String code,
        String name,
        String description,
        boolean assignableByCompany,
        Set<Permission> permissions,
        Instant createdAt
) {

    public RoleTemplate {
        permissions = permissions == null || permissions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    /** A template the platform admin adds — id, code and createdAt belong to the adapter. */
    public static RoleTemplate custom(String name, String description, boolean assignableByCompany,
                                      Set<Permission> permissions) {
        return new RoleTemplate(0, null, name, description, assignableByCompany, permissions, null);
    }

    /** The first-run seed: today's code-defined role becomes an editable template. */
    public static RoleTemplate fromSystemRole(SystemRole systemRole) {
        return new RoleTemplate(0, systemRole.name(), systemRole.label(), null,
                systemRole.assignableByCompany(), systemRole.resolvePermissions(), null);
    }
}
