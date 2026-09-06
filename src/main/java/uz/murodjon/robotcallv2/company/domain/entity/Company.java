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

    /** A brand-new tenant: everything but the name is the storage layer's to fill in. */
    public static Company named(String name) {
        return new Company(0, name, null, null, null, null);
    }

    /** The editable profile of an existing tenant, for a write. */
    public static Company profile(String name, String address) {
        return new Company(0, name, null, null, null, address);
    }
}
