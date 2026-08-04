package uz.murodjon.uysotvoice.company.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/companies/{id}} — a tenant's own {@code ADMIN} editing its identity
 * (report #3). {@code status} is deliberately not here — see {@link
 * UpdateCompanyStatusRequest} / {@code PUT /api/companies/{id}/status}, SUPERADMIN-only.
 * The logo is not here either — it only ever changes through {@code POST
 * /api/companies/{id}/logo}, never as an arbitrary id/URL a caller can set directly.
 */
public record UpdateCompanyRequest(
        @NotBlank String name,
        String address
) {
}
