package uz.murodjon.robotcallv2.profile.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Switching two-factor login off takes both factors: a stolen session alone must not be
 * able to remove the second one.
 */
public record DisableTwoFactorRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 6) String code
) {
}
