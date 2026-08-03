package uz.murodjon.uysotvoice.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/profile/password} body — self-service "Xavfsizlik" tab (UI-DESIGN §8.3). */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword
) {
}
