package uz.murodjon.robotcallv2.role.domain.entity;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A named set of permissions inside one company (app_role). Every role's permissions are stored and
 * the company may edit any of them. {@link #code} is set when the role was copied from a platform
 * template (ADMIN, OPERATOR, …) — that is how code finds "the company's ADMIN role". A role with
 * {@code assignableByCompany = false} (DEVELOPER) is handed out and edited by platform staff only.
 */
public record Role(
        long id,
        long companyId,
        String code,
        String name,
        String description,
        boolean assignableByCompany,
        Set<Permission> permissions,
        Instant createdAt
) {

    public Role {
        permissions = permissions == null || permissions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    /** A role the company defines itself — id, companyId and createdAt belong to the adapter. */
    public static Role custom(String name, String description, Set<Permission> permissions) {
        return new Role(0, 0, null, name, description, true, permissions, null);
    }

    /** A new company's copy of a platform template. */
    public static Role fromTemplate(RoleTemplate template) {
        return new Role(0, 0, template.code(), template.name(), template.description(),
                template.assignableByCompany(), template.permissions(), null);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
