package uz.murodjon.robotcallv2.profile.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;
import uz.murodjon.robotcallv2.auth.application.service.SessionService;
import uz.murodjon.robotcallv2.profile.application.port.input.ProfileSessionUseCase;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

import java.util.List;

@Service
public class ProfileSessionService implements ProfileSessionUseCase {

    private final SessionService sessions;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ProfileSessionService(SessionService sessions, CurrentUser currentUser, AuditService audit) {
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Override
    public List<UserSessionRow> list() {
        return sessions.listActive(requireUserId());
    }

    @Override
    public void revoke(long id) {
        long userId = requireUserId();
        sessions.revoke(id, userId);
        audit.record("PROFILE_SESSION_REVOKE", "user_session", String.valueOf(id), null);
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
