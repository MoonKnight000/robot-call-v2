package uz.murodjon.robotcallv2.contact.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Phone is not editable here — it is the contact's stable identity key (§10.8). */
public record UpdateContactRequest(
        @NotBlank String name,
        String address,
        String tags,
        String notes
) {
}
