package uz.murodjon.uysotvoice.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/auth/reset-password} body — sets a new password for the account behind {@code token}. */
public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8) String newPassword) {
}
