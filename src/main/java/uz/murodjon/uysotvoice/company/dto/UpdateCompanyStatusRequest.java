package uz.murodjon.uysotvoice.company.dto;

import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.company.enums.CompanyStatus;

/**
 * {@code PUT /api/companies/{id}/status} — SUPERADMIN-only (report #3): a tenant's own
 * {@code ADMIN} can no longer flip its own company between {@code ACTIVE}/{@code
 * SUSPENDED} via {@link UpdateCompanyRequest}.
 */
public record UpdateCompanyStatusRequest(
        @NotNull CompanyStatus status
) {
}
