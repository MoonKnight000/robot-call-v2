package uz.murodjon.robotcallv2.partner.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A partner's brand, as the partner (or the platform, enrolling it) sets it. Every field but the name is optional. */
public record UpdatePartnerBrandingRequest(
        @NotBlank @Size(max = 100) String productName,
        @Size(max = 255) @Pattern(regexp = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$") String domain,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$") String logoUrl,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String primaryColor,
        @Email @Size(max = 255) String supportEmail,
        @Size(max = 32) String supportPhone
) {
}
