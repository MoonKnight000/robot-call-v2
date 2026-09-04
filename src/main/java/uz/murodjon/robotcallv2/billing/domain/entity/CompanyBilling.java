package uz.murodjon.robotcallv2.billing.domain.entity;

import java.time.Instant;

public record CompanyBilling(
        Long id,
        long companyId,
        String planCode,
        String planName,
        long balanceUzs,
        boolean autoRecharge,
        Instant nextBillingDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static CompanyBilling defaultFor(long companyId) {
        return new CompanyBilling(
                null,
                companyId,
                "PRO_MONTHLY",
                "Professional (Pro)",
                1450000L,
                true,
                Instant.now().plusSeconds(30L * 24 * 3600),
                Instant.now(),
                Instant.now()
        );
    }
}
