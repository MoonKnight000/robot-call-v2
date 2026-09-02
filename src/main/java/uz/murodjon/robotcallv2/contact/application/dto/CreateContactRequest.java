package uz.murodjon.robotcallv2.contact.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateContactRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String address,
        String tags,
        String notes
) {
}
