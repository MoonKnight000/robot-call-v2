package uz.murodjon.uysotvoice.user.domain;

import uz.murodjon.uysotvoice.user.enums.UserRole;
import uz.murodjon.uysotvoice.user.enums.UserStatus;

import java.time.Instant;

/**
 * The domain model of {@code app_user} (ROADMAP E.1) — the full account record passed
 * between {@code auth}/{@code profile}/{@code user} services and {@link
 * uz.murodjon.uysotvoice.user.repository.UserRepository}. Carries the password hash and
 * one-time tokens, so it never reaches a controller directly; API responses go through
 * {@link uz.murodjon.uysotvoice.user.dto.UserRow}.
 */
public record User(
        long id,
        long companyId,
        String name,
        String username,
        String email,
        String passwordHash,
        UserRole role,
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
