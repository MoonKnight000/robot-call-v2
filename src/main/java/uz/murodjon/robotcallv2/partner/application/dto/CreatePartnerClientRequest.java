package uz.murodjon.robotcallv2.partner.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A client company a partner signs up, with its first administrator. */
public record CreatePartnerClientRequest(
        @NotBlank @Size(max = 120) String companyName,
        @NotBlank @Size(max = 120) String adminName,
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 190) String email
) {
}
