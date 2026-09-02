package uz.murodjon.robotcallv2.company.domain.entity;

import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;

import java.time.Instant;

/**
 * Domain record for company (ROADMAP B.1).
 */
public record Company(
        long id,
        String name,
        CompanyStatus status,
        Instant createdAt,
        Long logoFileId,
        String address
) {
}
