package uz.murodjon.uysotvoice.profile.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.auth.dto.UserSession;
import uz.murodjon.uysotvoice.auth.service.SessionService;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.util.List;

/** Self-service "Xavfsizlik" tab's "faol sessiyalar" list (API-REQUIREMENTS §15, UI-DESIGN §8.3). */
@Service
public class ProfileSessionService {

    private final SessionService sessions;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ProfileSessionService(SessionService sessions, CurrentUser currentUser, AuditService audit) {
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public List<UserSession> list() {
        return sessions.listActive(requireUserId());
    }

    /** No-op if {@code id} does not belong to the caller — same shape as every other revoke in this codebase. */
    public void revoke(long id) {
        long userId = requireUserId();
        sessions.revoke(id, userId);
        audit.record("PROFILE_SESSION_REVOKE", "user_session", String.valueOf(id), null);
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException("no user session on this request"));
    }
}
