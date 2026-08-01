package uz.murodjon.uysotvoice.contact.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param tags comma-separated free-form labels
 */
public record CreateContactRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String address,
        String tags,
        String notes
) {
}
