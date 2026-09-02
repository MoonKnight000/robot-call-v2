package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.user.application.dto.UserRow;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserRow user
) {
}
