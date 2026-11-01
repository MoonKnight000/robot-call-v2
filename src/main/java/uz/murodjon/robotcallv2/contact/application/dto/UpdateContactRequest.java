package uz.murodjon.robotcallv2.contact.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Phone is not editable here — it is the contact's stable identity key (§10.8).
 * {@code customFields}: null keeps the stored set, a map replaces the whole set.
 */
public record UpdateContactRequest(
        @NotBlank String name,
        String address,
        String tags,
        String notes,
        Map<String, String> customFields
) {
}
