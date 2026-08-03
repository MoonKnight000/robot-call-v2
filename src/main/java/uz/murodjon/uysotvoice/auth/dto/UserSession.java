package uz.murodjon.uysotvoice.auth.dto;

import java.time.Instant;

/** Row for {@code GET /api/profile/sessions} (API-REQUIREMENTS §15 "faol sessiyalar"). */
public record UserSession(
        long id,
        String device,
        String ipAddress,
        Instant createdAt,
        Instant lastActivityAt
) {
}
