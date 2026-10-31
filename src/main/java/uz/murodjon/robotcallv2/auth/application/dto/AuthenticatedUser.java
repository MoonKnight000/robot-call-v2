package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.user.domain.enums.UserType;

import java.util.Set;

/**
 * The identity behind a Bearer token: who is calling, for which company (null for platform admin),
 * and exactly what they may do.
 */
public record AuthenticatedUser(
        long userId,
        Long companyId,
        Long roleId,
        String roleCode,
        Set<Permission> permissions,
        String name,
        String email,
        UserType type
) {

    public AuthenticatedUser(long userId, Long companyId, Long roleId, String roleCode,
                             Set<Permission> permissions, String name, String email) {
        this(userId, companyId, roleId, roleCode, permissions, name, email,
                companyId == null ? UserType.PLATFORM_ADMIN : UserType.COMPANY_USER);
    }

    public boolean hasPermission(Permission permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean isPlatformAdmin() {
        return type == UserType.PLATFORM_ADMIN || hasPermission(Permission.PLATFORM_ADMIN);
    }
}
