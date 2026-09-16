package uz.murodjon.robotcallv2.profile.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param code the 6-digit code the authenticator app shows now */
public record TwoFactorCodeRequest(
        @NotBlank @Size(min = 6, max = 6) String code
) {
}
