package uz.murodjon.robotcallv2.user.domain.entity;

import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;
import uz.murodjon.robotcallv2.user.domain.enums.UserType;

import java.time.Instant;

/**
 * Domain model of app_user (ROADMAP E.1).
 * PLATFORM_ADMIN has null companyId and null roleId.
 * COMPANY_USER has non-null companyId and non-null roleId.
 */
public record User(
        long id,
        Long companyId,
        String name,
        String username,
        String email,
        String passwordHash,
        Long roleId,
        String roleCode,
        String roleName,
        UserStatus status,
        UserType type,
        String inviteTokenHash,
        Instant inviteExpiresAt,
        String resetTokenHash,
        Instant resetExpiresAt,
        Instant lastLoginAt,
        Instant createdAt,
        String phone,
        String position,
        Long avatarFileId,
        String callColumns,
        String sipExtension
) {

    public User(
            long id,
            Long companyId,
            String name,
            String username,
            String email,
            String passwordHash,
            Long roleId,
            String roleCode,
            String roleName,
            UserStatus status,
            String inviteTokenHash,
            Instant inviteExpiresAt,
            String resetTokenHash,
            Instant resetExpiresAt,
            Instant lastLoginAt,
            Instant createdAt,
            String phone,
            String position,
            Long avatarFileId,
            String callColumns,
            String sipExtension
    ) {
        this(id, companyId, name, username, email, passwordHash, roleId, roleCode, roleName, status,
                companyId == null ? UserType.PLATFORM_ADMIN : UserType.COMPANY_USER,
                inviteTokenHash, inviteExpiresAt, resetTokenHash, resetExpiresAt, lastLoginAt,
                createdAt, phone, position, avatarFileId, callColumns, sipExtension);
    }

    public boolean isPlatformAdmin() {
        return type == UserType.PLATFORM_ADMIN;
    }
}
