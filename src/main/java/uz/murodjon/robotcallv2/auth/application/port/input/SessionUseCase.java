package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;

import java.util.List;

public interface SessionUseCase {

    List<UserSessionRow> listActiveSessions(long userId, String currentRefreshToken);

    /** The requesting user's active sessions; the one holding {@code currentRefreshToken} is marked current. */
    List<UserSessionRow> listOwnSessions(String currentRefreshToken);

    void revoke(long sessionId, long userId);

    void revokeAllForUser(long userId);
}
