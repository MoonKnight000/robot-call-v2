package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.auth.application.port.output.UserSessionRepository;
import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;
import uz.murodjon.robotcallv2.shared.util.Tokens;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SessionService implements SessionUseCase {

    private final UserSessionRepository sessions;

    public SessionService(UserSessionRepository sessions) {
        this.sessions = sessions;
    }

    public long create(long companyId, long userId, String tokenHash, Instant expiresAt) {
        return sessions.create(companyId, userId, tokenHash, expiresAt, currentDevice(), currentIp());
    }

    public Optional<UserSession> findActiveByHash(String tokenHash) {
        return sessions.findActiveByHash(tokenHash);
    }

    public void rotate(long sessionId, String newTokenHash, Instant newExpiresAt) {
        sessions.rotate(sessionId, newTokenHash, newExpiresAt, currentDevice(), currentIp());
    }

    @Override
    public void revokeAllForUser(long userId) {
        sessions.revokeAllForUser(userId);
    }

    @Override
    public void revoke(long sessionId, long userId) {
        sessions.revoke(sessionId, userId);
    }

    public List<UserSessionRow> listActive(long userId) {
        return sessions.listActiveForUser(userId).stream()
                .map(s -> UserSessionRow.of(s, false))
                .toList();
    }

    @Override
    public List<UserSessionRow> listActiveSessions(long userId, String currentRefreshToken) {
        String currentHash = currentRefreshToken != null ? Tokens.hash(currentRefreshToken) : null;
        return sessions.listActiveForUser(userId).stream()
                .map(s -> UserSessionRow.of(s, currentHash != null && currentHash.equals(s.refreshTokenHash())))
                .toList();
    }

    private static String currentDevice() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        String userAgent = servletAttrs.getRequest().getHeader("User-Agent");
        return userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255));
    }

    private static String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return servletAttrs.getRequest().getRemoteAddr();
    }
}
