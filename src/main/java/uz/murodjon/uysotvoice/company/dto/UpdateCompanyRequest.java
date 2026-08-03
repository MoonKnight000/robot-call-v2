package uz.murodjon.uysotvoice.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.company.enums.CompanyStatus;

public record UpdateCompanyRequest(
        @NotBlank String name,
        @NotNull CompanyStatus status,
        String logoUrl,
        String address
) {
}
