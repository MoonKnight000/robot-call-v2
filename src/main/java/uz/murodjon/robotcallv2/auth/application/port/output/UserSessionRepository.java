package uz.murodjon.robotcallv2.auth.application.port.output;

import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository {

    long create(long companyId, long userId, String tokenHash, Instant expiresAt,
                String device, String ipAddress);

    Optional<UserSession> findActiveByHash(String tokenHash);

    void rotate(long id, String newTokenHash, Instant newExpiresAt, String device, String ipAddress);

    void revoke(long id, long userId);

    void revokeAllForUser(long userId);

    List<UserSession> listActiveForUser(long userId);
}
