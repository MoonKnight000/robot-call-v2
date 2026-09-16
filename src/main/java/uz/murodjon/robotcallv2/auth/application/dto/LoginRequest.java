package uz.murodjon.robotcallv2.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param otpCode the authenticator code; required only for an account with two-factor
 *                login on, which is refused with {@code TOTP_REQUIRED} until it is sent
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @Size(min = 6, max = 6) String otpCode
) {

    public LoginRequest(String username, String password) {
        this(username, password, null);
    }
}
