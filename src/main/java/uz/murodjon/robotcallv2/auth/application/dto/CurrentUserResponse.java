package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Set;

/**
 * What the panel needs right after login: who is signed in, and every permission the role
 * grants so the frontend can decide which pages to render at all.
 */
public record CurrentUserResponse(
        long id,
        String name,
        String username,
        String email,
        long roleId,
        String roleCode,
        String roleName,
        Set<Permission> permissions,
        long companyId,
        String companyName
) {
}
