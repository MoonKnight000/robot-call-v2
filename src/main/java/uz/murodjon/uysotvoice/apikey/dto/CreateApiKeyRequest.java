package uz.murodjon.uysotvoice.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.user.enums.UserRole;

public record CreateApiKeyRequest(
        @NotBlank String name,
        @NotNull UserRole role
) {
}
