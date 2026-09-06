package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Set;

/**
 * The identity behind a Bearer token: who is calling, for which company, and exactly what
 * they may do. The permission set is decoded from the token, not looked up per request —
 * which is why a role change revokes its holders' sessions (RoleService#update).
 */
public record AuthenticatedUser(
        long userId,
        long companyId,
        long roleId,
        String roleCode,
        Set<Permission> permissions,
        String name,
        String email
) {

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
