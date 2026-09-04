package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;

import java.util.Optional;

public interface CompanyBillingRepository {

    Optional<CompanyBilling> findByCompanyId(long companyId);

    CompanyBilling save(CompanyBilling billing);

    void addBalance(long companyId, long amountUzs);
}
