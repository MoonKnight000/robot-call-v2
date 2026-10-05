package uz.murodjon.robotcallv2.profile.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.profile.application.port.input.ProfileSessionUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;

import java.util.List;

@Service
public class ProfileSessionService implements ProfileSessionUseCase {

    private final SessionUseCase sessionUseCase;
    private final CurrentUser currentUser;
    private final AuditUseCase audit;

    public ProfileSessionService(SessionUseCase sessionUseCase, CurrentUser currentUser, AuditUseCase audit) {
        this.sessionUseCase = sessionUseCase;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** No refresh token reaches this endpoint, so no row is marked as the current one. */
    @Override
    public List<UserSessionRow> list() {
        return sessionUseCase.listActiveSessions(requireUserId(), null);
    }

    @Override
    public void revoke(long companyId, long id) {
        long userId = requireUserId();
        sessionUseCase.revoke(id, userId);
        audit.record(companyId, "PROFILE_SESSION_REVOKE", "user_session", String.valueOf(id), null);
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
