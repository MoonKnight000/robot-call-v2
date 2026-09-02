package uz.murodjon.robotcallv2.auth.domain.entity;

import java.time.Instant;

/**
 * Domain model of user_session (API-REQUIREMENTS §15).
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
