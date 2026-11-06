package uz.murodjon.robotcallv2.contact.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;

import java.util.List;

/** PUT /api/contacts/fields/{id} — the key and the type cannot change; every other value is replaced. */
public record UpdateContactFieldRequest(
        @NotBlank @Size(max = 128) String label,
        List<String> options,
        @Size(max = 2000) String description,
        @Size(max = 64) String extractionKey,
        ContactFieldUpdateMode updateMode
) {
}
