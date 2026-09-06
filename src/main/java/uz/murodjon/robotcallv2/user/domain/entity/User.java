package uz.murodjon.robotcallv2.user.domain.entity;

import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;

/**
 * Domain model of app_user (ROADMAP E.1). The role is carried by id plus the two fields
 * every screen shows next to it, so listing users never has to fan out into the role table.
 */
public record User(
        long id,
        long companyId,
        String name,
        String username,
        String email,
        String passwordHash,
        long roleId,
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
}
