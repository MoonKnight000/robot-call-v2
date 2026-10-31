package uz.murodjon.robotcallv2.user.application.dto;

import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;
import uz.murodjon.robotcallv2.user.domain.enums.UserType;

import java.time.Instant;

/** Row for GET /api/users (UI-DESIGN §10.12). */
public record UserRow(
        long id,
        Long companyId,
        String name,
        String username,
        String email,
        Long roleId,
        String roleCode,
        String roleName,
        UserStatus status,
        UserType type,
        Instant lastLoginAt,
        Instant createdAt
) {

    public UserRow(
            long id,
            Long companyId,
            String name,
            String username,
            String email,
            Long roleId,
            String roleCode,
            String roleName,
            UserStatus status,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        this(id, companyId, name, username, email, roleId, roleCode, roleName, status,
                companyId == null ? UserType.PLATFORM_ADMIN : UserType.COMPANY_USER,
                lastLoginAt, createdAt);
    }

    public static UserRow of(User u) {
        return new UserRow(u.id(), u.companyId(), u.name(), u.username(), u.email(), u.roleId(), u.roleCode(),
                u.roleName(), u.status(), u.type(), u.lastLoginAt(), u.createdAt());
    }
}
