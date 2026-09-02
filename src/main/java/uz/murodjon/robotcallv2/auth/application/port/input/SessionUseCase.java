package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;

import java.util.List;

public interface SessionUseCase {

    List<UserSessionRow> listActiveSessions(long userId, String currentRefreshToken);

    void revoke(long sessionId, long userId);

    void revokeAllForUser(long userId);
}
