package uz.murodjon.uysotvoice.user.dto;

import uz.murodjon.uysotvoice.user.enums.UserRole;
import uz.murodjon.uysotvoice.user.enums.UserStatus;

import java.time.Instant;

/** Row for {@code GET /api/users} (UI-DESIGN §10.12) — never carries the password hash. */
public record User(
        long id,
        long companyId,
        String name,
        String username,
        String email,
        UserRole role,
        UserStatus status,
        Instant lastLoginAt,
        Instant createdAt
) {
}
