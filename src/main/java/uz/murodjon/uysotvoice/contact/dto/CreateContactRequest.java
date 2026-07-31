package uz.murodjon.uysotvoice.contact.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param tags comma-separated, matching {@code campaign.dial_days}'s convention
 */
public record CreateContactRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String address,
        String tags,
        String notes
) {
}
