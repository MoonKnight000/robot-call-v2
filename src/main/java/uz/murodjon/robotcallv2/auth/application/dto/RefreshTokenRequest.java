package uz.murodjon.robotcallv2.auth.application.dto;

/**
 * @param refreshToken optional since the token travels in the {@code refresh_token} cookie;
 *                     sending it in the body is deprecated and still wins when present
 */
public record RefreshTokenRequest(
        String refreshToken
) {
}
