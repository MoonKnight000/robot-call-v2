package uz.murodjon.uysotvoice.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import uz.murodjon.uysotvoice.auth.dto.UserSession;
import uz.murodjon.uysotvoice.auth.entity.UserSessionEntity;
import uz.murodjon.uysotvoice.auth.repository.UserSessionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Refresh-token session lifecycle ({@code user_session}) — one row per logged-in device.
 * {@link AuthService} drives {@link #create}/{@link #rotate}/{@link #revokeAll} on
 * login/refresh/logout; {@code profile.service.ProfileService} drives {@link #listActive}/
 * {@link #revoke} for the self-service "faol sessiyalar" tab (API-REQUIREMENTS §15).
 */
@Service
public class SessionService {

    private final UserSessionRepository sessions;

    public SessionService(UserSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** New session row for a fresh login — does not disturb the caller's other devices. */
    public long create(long companyId, long userId, String tokenHash, Instant expiresAt) {
        return sessions.create(companyId, userId, tokenHash, expiresAt, currentDevice(), currentIp());
    }

    public Optional<UserSessionEntity> findActiveByHash(String tokenHash) {
        return sessions.findActiveByHash(tokenHash);
    }

    /** {@code POST /api/auth/refresh} — same session id, new token/expiry. */
    public void rotate(long sessionId, String newTokenHash, Instant newExpiresAt) {
        sessions.rotate(sessionId, newTokenHash, newExpiresAt, currentDevice(), currentIp());
    }

    /** {@code POST /api/auth/logout} — the request carries no session id, so every device is signed out. */
    public void revokeAll(long userId) {
        sessions.revokeAllForUser(userId);
    }

    /** {@code DELETE /api/profile/sessions/{id}} — ends one specific device's session. */
    public void revoke(long sessionId, long userId) {
        sessions.revoke(sessionId, userId);
    }

    public List<UserSession> listActive(long userId) {
        return sessions.listActiveForUser(userId);
    }

    /**
     * The caller's {@code User-Agent}, or {@code null} off-request — same {@link
     * RequestContextHolder} trick as {@code AuditService#currentIp}.
     */
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
