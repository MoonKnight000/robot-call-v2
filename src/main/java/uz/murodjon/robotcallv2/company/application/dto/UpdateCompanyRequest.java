package uz.murodjon.robotcallv2.company.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String address
) {
}
