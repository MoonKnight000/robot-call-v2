package uz.murodjon.robotcallv2.company.application.port.output;

import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;

public interface CompanyConfigRepository {

    boolean existsByCompanyId(long companyId);

    long create(long companyId, CompanyConfig config);

    CompanyConfig find(long companyId);

    void update(long companyId, CompanyConfig config);
}
