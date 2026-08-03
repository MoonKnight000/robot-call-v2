package uz.murodjon.uysotvoice.company.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A new company starts with a default {@link CompanyConfig} ({@code uz-UZ}, 09:00-20:00,
 * {@code Asia/Tashkent}) — edit it via {@code PUT /api/companies/{id}/config} afterwards.
 */
public record CreateCompanyRequest(
        @NotBlank String name
) {
}
