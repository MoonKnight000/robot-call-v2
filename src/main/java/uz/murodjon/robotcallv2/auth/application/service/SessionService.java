package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.auth.application.port.output.UserSessionRepository;
import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.util.Tokens;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SessionService implements SessionUseCase {

    private final UserSessionRepository repository;
    private final TokenRevocationService tokenRevocation;
    /** The device and address a session is opened or rotated from; both empty off a request thread. */
    private final CurrentUser currentUser;

    public SessionService(UserSessionRepository repository, TokenRevocationService tokenRevocation,
                          CurrentUser currentUser) {
        this.repository = repository;
        this.tokenRevocation = tokenRevocation;
        this.currentUser = currentUser;
    }

    public long create(long companyId, long userId, String tokenHash, Instant expiresAt) {
        return repository.create(companyId, userId, tokenHash, expiresAt,
                currentUser.userAgent().orElse(null), currentUser.remoteAddress().orElse(null));
    }

    public Optional<UserSession> findActiveByHash(String tokenHash) {
        return repository.findActiveByHash(tokenHash);
    }

    /** The session whose refresh token was rotated away from {@code tokenHash}, if any. */
    public Optional<UserSession> findByPreviousHash(String tokenHash) {
        return repository.findByPreviousHash(tokenHash);
    }

    public void rotate(long sessionId, String newTokenHash, Instant newExpiresAt) {
        repository.rotate(sessionId, newTokenHash, newExpiresAt,
                currentUser.userAgent().orElse(null), currentUser.remoteAddress().orElse(null));
    }

    /**
     * Every device out, and every access token already in the user's hands with it — a
     * logout, password change, role change or block that left a 60-minute token alive
     * would not have revoked anything the holder could not simply keep using.
     */
    @Override
    public void revokeAllForUser(long userId) {
        repository.revokeAllForUser(userId);
        tokenRevocation.revokeAllForUser(userId);
    }

    @Override
    public void revoke(long sessionId, long userId) {
        repository.revoke(sessionId, userId);
    }

    @Override
    public List<UserSessionRow> listActiveSessions(long userId, String currentRefreshToken) {
        String currentHash = currentRefreshToken != null ? Tokens.hash(currentRefreshToken) : null;
        return repository.listActiveForUser(userId).stream()
                .map(s -> UserSessionRow.of(s, currentHash != null && currentHash.equals(s.refreshTokenHash())))
                .toList();
    }
}
