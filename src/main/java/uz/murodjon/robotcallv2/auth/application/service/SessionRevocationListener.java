package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.company.domain.event.CompanyFeaturesChangedEvent;
import uz.murodjon.robotcallv2.company.domain.event.CompanySuspendedEvent;
import uz.murodjon.robotcallv2.role.domain.event.RoleChangedEvent;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.event.UserBlockedEvent;
import uz.murodjon.robotcallv2.user.domain.event.UserRoleChangedEvent;

/**
 * Ends sessions when something outside auth takes a right away. The company, user and role
 * features announce what they changed instead of calling back into auth, which keeps the
 * dependency one way (auth → them). Plain {@link EventListener}s run on the publishing
 * thread, inside its transaction, before {@code publishEvent} returns — the revocation
 * happens exactly when the old direct call did.
 */
@Component
public class SessionRevocationListener {

    private final SessionUseCase sessionUseCase;
    private final UserUseCase userUseCase;

    public SessionRevocationListener(SessionUseCase sessionUseCase, UserUseCase userUseCase) {
        this.sessionUseCase = sessionUseCase;
        this.userUseCase = userUseCase;
    }

    @EventListener
    public void revokeOnCompanyFeaturesChanged(CompanyFeaturesChangedEvent event) {
        userUseCase.list(event.companyId()).forEach(user -> sessionUseCase.revokeAllForUser(user.id()));
    }

    /** Login and refresh refuse a suspended company; this ends the sessions already open. */
    @EventListener
    public void revokeOnCompanySuspended(CompanySuspendedEvent event) {
        userUseCase.list(event.companyId()).forEach(user -> sessionUseCase.revokeAllForUser(user.id()));
    }

    @EventListener
    public void revokeOnUserBlocked(UserBlockedEvent event) {
        sessionUseCase.revokeAllForUser(event.userId());
    }

    @EventListener
    public void revokeOnUserRoleChanged(UserRoleChangedEvent event) {
        sessionUseCase.revokeAllForUser(event.userId());
    }

    @EventListener
    public void revokeOnRoleChanged(RoleChangedEvent event) {
        event.userIds().forEach(sessionUseCase::revokeAllForUser);
    }
}
