package uz.murodjon.uysotvoice.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/refresh} body — exchanges a still-valid refresh token for a new pair. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
