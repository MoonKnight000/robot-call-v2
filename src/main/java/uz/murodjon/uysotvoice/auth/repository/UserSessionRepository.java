package uz.murodjon.uysotvoice.auth.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.auth.dto.UserSession;
import uz.murodjon.uysotvoice.auth.entity.UserSessionEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed DAO for {@code user_session} — the concurrent-login-friendly replacement
 * for {@code app_user}'s old single refresh-token column (API-REQUIREMENTS §15).
 */
@Repository
public class UserSessionRepository {

    private final UserSessionJpaRepository jpa;

    public UserSessionRepository(UserSessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    public long create(long companyId, long userId, String tokenHash, Instant expiresAt,
                        String device, String ipAddress) {
        UserSessionEntity entity = new UserSessionEntity();
        entity.setCompanyId(companyId);
        entity.setUserId(userId);
        entity.setRefreshTokenHash(tokenHash);
        entity.setDevice(device);
        entity.setIpAddress(ipAddress);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setLastActivityAt(now);
        entity.setExpiresAt(expiresAt);
        return jpa.save(entity).getId();
    }

    /** Not revoked — a revoked session's old hash must never authenticate a refresh again. */
    public Optional<UserSessionEntity> findActiveByHash(String tokenHash) {
        return jpa.findByRefreshTokenHashAndRevokedAtIsNull(tokenHash);
    }

    /** Rotates the token in place — same session id/device/created_at, new hash+expiry. */
    public void rotate(long id, String newTokenHash, Instant newExpiresAt, String device, String ipAddress) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setRefreshTokenHash(newTokenHash);
            entity.setExpiresAt(newExpiresAt);
            entity.setLastActivityAt(Instant.now());
            if (device != null) {
                entity.setDevice(device);
            }
            if (ipAddress != null) {
                entity.setIpAddress(ipAddress);
            }
            jpa.save(entity);
        });
    }

    /** No-op if {@code id} does not belong to {@code userId} — same ownership-check shape as apikey/revoke. */
    public void revoke(long id, long userId) {
        jpa.findByIdAndUserId(id, userId).ifPresent(entity -> {
            entity.setRevokedAt(Instant.now());
            jpa.save(entity);
        });
    }

    public void revokeAllForUser(long userId) {
        jpa.findByUserIdAndRevokedAtIsNullOrderByLastActivityAtDesc(userId)
                .forEach(entity -> {
                    entity.setRevokedAt(Instant.now());
                    jpa.save(entity);
                });
    }

    public List<UserSession> listActiveForUser(long userId) {
        return jpa.findByUserIdAndRevokedAtIsNullOrderByLastActivityAtDesc(userId).stream()
                .map(e -> new UserSession(e.getId(), e.getDevice(), e.getIpAddress(), e.getCreatedAt(),
                        e.getLastActivityAt()))
                .toList();
    }
}
