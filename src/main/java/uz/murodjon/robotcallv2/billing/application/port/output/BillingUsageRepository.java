package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.BillingUsage;

import java.util.List;
import java.util.Optional;

public interface BillingUsageRepository {

    Optional<BillingUsage> findByCompanyIdAndPeriod(long companyId, String billingPeriod);

    List<BillingUsage> findRecentByCompanyId(long companyId, int limit);

    BillingUsage save(BillingUsage usage);
}
