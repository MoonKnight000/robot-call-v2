package uz.murodjon.uysotvoice.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.user.enums.UserRole;

/** {@code POST /api/users/invite} body (UI-DESIGN §10.12). */
public record InviteUserRequest(
        @NotBlank String name,
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotNull UserRole role
) {
}
