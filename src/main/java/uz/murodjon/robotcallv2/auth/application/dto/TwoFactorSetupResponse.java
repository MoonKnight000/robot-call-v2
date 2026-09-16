package uz.murodjon.robotcallv2.auth.application.dto;

/**
 * What the user needs to add the account to an authenticator app.
 *
 * @param secret     the Base32 secret, for typing in by hand
 * @param otpauthUri the same as an {@code otpauth://} link, for the QR code
 */
public record TwoFactorSetupResponse(
        String secret,
        String otpauthUri
) {
}
