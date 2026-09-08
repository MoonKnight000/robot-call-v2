package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.BillingUsage;

import java.util.Optional;

/**
 * The plan allowances for a billing period.
 *
 * <p>What a company has actually used against them is not read from here — it is summed
 * from the calls it was charged for ({@link CallBillingRepository}).
 */
public interface BillingUsageRepository {

    Optional<BillingUsage> findByCompanyIdAndPeriod(long companyId, String billingPeriod);

    BillingUsage save(BillingUsage usage);
}
