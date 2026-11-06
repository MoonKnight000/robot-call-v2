package uz.murodjon.robotcallv2.role.application.dto;

import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.Set;

/**
 * Row for the roles page — the role plus how many users currently hold it. {@code system} now means
 * "copied from a platform template" (it has a {@code code}); such a role is as editable as any other.
 */
public record RoleRow(
        long id,
        String code,
        String name,
        String description,
        boolean system,
        boolean assignableByCompany,
        Set<Permission> permissions,
        long userCount,
        Instant createdAt
) {

    public static RoleRow of(Role role, long userCount) {
        return new RoleRow(role.id(), role.code(), role.name(), role.description(), role.code() != null,
                role.assignableByCompany(), role.permissions(), userCount, role.createdAt());
    }
}
