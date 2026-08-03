package uz.murodjon.uysotvoice.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/auth/activate} body — sets the password for an invited account. */
public record ActivateRequest(@NotBlank String token, @NotBlank @Size(min = 8) String password) {
}
