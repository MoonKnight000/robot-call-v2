package uz.murodjon.robotcallv2.auth.application.port.output;

import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository {

    long create(Long companyId, long userId, String tokenHash, Instant expiresAt,
                String device, String ipAddress);

    Optional<UserSession> findActiveByHash(String tokenHash);

    /** The session that rotated away from {@code tokenHash} — a token presented after its rotation. */
    Optional<UserSession> findByPreviousHash(String tokenHash);

    /** Replaces the refresh token, remembering the one it replaces for reuse detection. */
    void rotate(long id, String newTokenHash, Instant newExpiresAt, String device, String ipAddress);

    void revoke(long id, long userId);

    void revokeAllForUser(long userId);

    List<UserSession> listActiveForUser(long userId);
}
