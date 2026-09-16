package uz.murodjon.robotcallv2.auth.application.dto;

import uz.murodjon.robotcallv2.user.application.dto.UserRow;

import java.time.Instant;

/**
 * @param refreshToken deprecated in the body: the same token is set as the
 *                     {@code refresh_token} HttpOnly cookie, which is where clients should
 *                     take it from; kept here until every client has moved over
 */
public record LoginResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserRow user
) {
}
