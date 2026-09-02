package uz.murodjon.robotcallv2.company.application.port.input;

import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyConfigRequest;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;

public interface CompanyConfigUseCase {

    void createDefault(long companyId);

    CompanyConfig find(long companyId);

    CompanyConfig requireConfig(long companyId);

    CompanyConfig requireConfigForApi(long companyId);

    CompanyConfig update(long companyId, UpdateCompanyConfigRequest r);

    String resolveLanguage(long companyId, String requested);
}
