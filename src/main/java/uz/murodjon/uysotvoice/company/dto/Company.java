package uz.murodjon.uysotvoice.company.dto;

import uz.murodjon.uysotvoice.company.enums.CompanyStatus;

import java.time.Instant;

/** One row of {@code GET/POST /api/companies...} (ROADMAP B.1). Settings: {@link CompanyConfig}. */
public record Company(
        long id,
        String name,
        CompanyStatus status,
        Instant createdAt,
        Long logoFileId,
        String address
) {
}
