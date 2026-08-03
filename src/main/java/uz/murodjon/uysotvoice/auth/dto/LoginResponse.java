package uz.murodjon.uysotvoice.auth.dto;

import uz.murodjon.uysotvoice.user.dto.User;

import java.time.Instant;

/**
 * Response for {@code POST /api/auth/login}, {@code POST /api/auth/activate} and
 * {@code POST /api/auth/refresh}. {@code accessToken} is the short-lived JWT sent as
 * {@code Authorization: Bearer <accessToken>}; {@code refreshToken} is a separate
 * long-lived, server-revocable opaque token (hash stored on {@code app_user}, ROADMAP
 * E.1 follow-up) exchanged at {@code POST /api/auth/refresh} for a new pair once the
 * access token expires, without asking for the password again.
 */
public record LoginResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        User me
) {
}
