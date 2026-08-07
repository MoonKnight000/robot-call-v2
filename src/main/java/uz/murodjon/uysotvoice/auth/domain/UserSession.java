package uz.murodjon.uysotvoice.auth.domain;

import java.time.Instant;

/**
 * The domain model of {@code user_session} — one row per logged-in device
 * (API-REQUIREMENTS §15 "faol sessiyalar"), passed between {@code AuthService}/{@code
 * SessionService} and {@link uz.murodjon.uysotvoice.auth.repository.UserSessionRepository}.
 * API responses go through {@link uz.murodjon.uysotvoice.auth.dto.UserSessionRow}.
 */
public record UserSession(
        long id,
        long companyId,
        long userId,
        String refreshTokenHash,
        String device,
        String ipAddress,
        Instant createdAt,
        Instant lastActivityAt,
        Instant expiresAt,
        Instant revokedAt
) {
}
