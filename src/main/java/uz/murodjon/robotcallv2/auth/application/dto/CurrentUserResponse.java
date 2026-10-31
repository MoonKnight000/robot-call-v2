package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.user.domain.enums.UserType;

import java.util.Set;

/**
 * What the panel needs right after login: who is signed in, and every permission the role
 * grants so the frontend can decide which pages to render at all.
 * For PLATFORM_ADMIN, companyId and roleId are null.
 */
public record CurrentUserResponse(
        long id,
        String name,
        String username,
        String email,
        Long roleId,
        String roleCode,
        String roleName,
        Set<Permission> permissions,
        Long companyId,
        String companyName,
        UserType type
) {

    public CurrentUserResponse(long id, String name, String username, String email,
                               Long roleId, String roleCode, String roleName,
                               Set<Permission> permissions, Long companyId, String companyName) {
        this(id, name, username, email, roleId, roleCode, roleName, permissions, companyId, companyName,
                companyId == null ? UserType.PLATFORM_ADMIN : UserType.COMPANY_USER);
    }
}
