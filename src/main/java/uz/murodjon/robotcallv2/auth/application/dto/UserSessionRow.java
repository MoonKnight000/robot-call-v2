package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;

import java.time.Instant;

public record UserSessionRow(
        long id,
        String device,
        String ipAddress,
        Instant createdAt,
        Instant lastActivityAt,
        boolean current
) {

    public static UserSessionRow of(UserSession s, boolean current) {
        return new UserSessionRow(s.id(), s.device(), s.ipAddress(), s.createdAt(), s.lastActivityAt(), current);
    }
}
