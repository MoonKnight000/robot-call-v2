package uz.murodjon.robotcallv2.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.shared.util.PasswordRules;

/**
 * @param otpCode the authenticator code; required only when the account has two-factor
 *                login on, so a reset link alone is not a way in
 */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
        @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
        String newPassword,
        @Size(min = 6, max = 6) String otpCode
) {

    public ResetPasswordRequest(String token, String newPassword) {
        this(token, newPassword, null);
    }
}
