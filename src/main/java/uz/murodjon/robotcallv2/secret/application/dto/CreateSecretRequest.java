package uz.murodjon.robotcallv2.secret.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSecretRequest(
        /** Referenced from a tool or webhook as {@code {{secrets.KEY}}}, hence the narrow alphabet. */
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String key,
        @NotBlank String value,
        @Size(max = 255) String description
) {
}
