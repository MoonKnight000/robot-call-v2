package uz.murodjon.robotcallv2.user.application.dto;

import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;

/** Row for GET /api/users (UI-DESIGN §10.12). */
public record UserRow(
        long id,
        long companyId,
        String name,
        String username,
        String email,
        long roleId,
        String roleCode,
        String roleName,
        UserStatus status,
        Instant lastLoginAt,
        Instant createdAt
) {

    public static UserRow of(User u) {
        return new UserRow(u.id(), u.companyId(), u.name(), u.username(), u.email(), u.roleId(), u.roleCode(),
                u.roleName(), u.status(), u.lastLoginAt(), u.createdAt());
    }
}
