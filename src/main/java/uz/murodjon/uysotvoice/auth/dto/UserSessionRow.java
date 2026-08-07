package uz.murodjon.uysotvoice.auth.dto;

import uz.murodjon.uysotvoice.auth.domain.UserSession;

import java.time.Instant;

/** Row for {@code GET /api/profile/sessions} (API-REQUIREMENTS §15 "faol sessiyalar"). */
public record UserSessionRow(
        long id,
        String device,
        String ipAddress,
        Instant createdAt,
        Instant lastActivityAt
) {

    public static UserSessionRow of(UserSession s) {
        return new UserSessionRow(s.id(), s.device(), s.ipAddress(), s.createdAt(), s.lastActivityAt());
    }
}
