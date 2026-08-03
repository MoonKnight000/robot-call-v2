package uz.murodjon.uysotvoice.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/login} body (UI-DESIGN §10.1). */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
