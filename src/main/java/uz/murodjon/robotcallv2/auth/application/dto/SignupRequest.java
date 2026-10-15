package uz.murodjon.robotcallv2.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new company registering itself. No password here: it is set from the activation link
 * mailed to {@code email}, which is what proves the address is real.
 */
public record SignupRequest(
        @NotBlank @Size(max = 120) String companyName,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 190) String email
) {
}
