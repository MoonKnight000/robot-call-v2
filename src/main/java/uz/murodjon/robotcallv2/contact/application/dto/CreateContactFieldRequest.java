package uz.murodjon.robotcallv2.contact.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldType;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;

import java.util.List;

/** POST /api/contacts/fields — {@code updateMode} null means OVERWRITE. */
public record CreateContactFieldRequest(
        @NotBlank @Size(max = 64) String key,
        @NotBlank @Size(max = 128) String label,
        @NotNull ContactFieldType type,
        List<String> options,
        @Size(max = 2000) String description,
        @Size(max = 64) String extractionKey,
        ContactFieldUpdateMode updateMode
) {
}
