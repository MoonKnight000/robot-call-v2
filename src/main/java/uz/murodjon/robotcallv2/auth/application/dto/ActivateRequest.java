package uz.murodjon.robotcallv2.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 6, max = 128) String password
) {
}
