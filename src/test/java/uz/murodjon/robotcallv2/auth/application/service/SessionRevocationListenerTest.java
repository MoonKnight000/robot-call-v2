package uz.murodjon.robotcallv2.auth.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.company.domain.event.CompanySuspendedEvent;
import uz.murodjon.robotcallv2.role.domain.event.RoleChangedEvent;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.event.UserBlockedEvent;
import uz.murodjon.robotcallv2.user.domain.event.UserRoleChangedEvent;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.util.List;

import static org.mockito.Mockito.*;

/** Each event that takes a right away ends the sessions of exactly the users it names. */
class SessionRevocationListenerTest {

    private static final long COMPANY_ID = 1L;

    private SessionUseCase sessionUseCase;
    private UserUseCase userUseCase;
    private SessionRevocationListener listener;

    @BeforeEach
    void setUp() {
        sessionUseCase = mock(SessionUseCase.class);
        userUseCase = mock(UserUseCase.class);
        listener = new SessionRevocationListener(sessionUseCase, userUseCase);
    }

    @Test
    void suspendedCompanyLosesTheSessionsOfEveryUser() {
        when(userUseCase.list(COMPANY_ID)).thenReturn(List.of(userRow(5L), userRow(6L)));

        listener.revokeOnCompanySuspended(new CompanySuspendedEvent(COMPANY_ID));

        verify(sessionUseCase).revokeAllForUser(5L);
        verify(sessionUseCase).revokeAllForUser(6L);
        verifyNoMoreInteractions(sessionUseCase);
    }

    @Test
    void blockedUserLosesTheirSessions() {
        listener.revokeOnUserBlocked(new UserBlockedEvent(COMPANY_ID, 5L));

        verify(sessionUseCase).revokeAllForUser(5L);
    }

    @Test
    void userWithANewRoleLosesTheirSessions() {
        listener.revokeOnUserRoleChanged(new UserRoleChangedEvent(COMPANY_ID, 5L));

        verify(sessionUseCase).revokeAllForUser(5L);
    }

    @Test
    void editedRoleEndsTheSessionsOfEveryHolder() {
        listener.revokeOnRoleChanged(new RoleChangedEvent(COMPANY_ID, 3L, List.of(5L, 6L)));

        verify(sessionUseCase).revokeAllForUser(5L);
        verify(sessionUseCase).revokeAllForUser(6L);
        verifyNoMoreInteractions(sessionUseCase);
    }

    private static UserRow userRow(long id) {
        return new UserRow(id, COMPANY_ID, "User " + id, "user" + id, "user" + id + "@example.com", 1L, "ADMIN",
                "Admin", UserStatus.ACTIVE, null, null);
    }
}
