package uz.murodjon.robotcallv2.company.application.port.input;

import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyConfigRequest;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;

import java.util.List;

public interface CompanyConfigUseCase {

    void createDefault(long companyId);

    CompanyConfig find(long companyId);

    /**
     * The BCP-47 codes a caller of this company may answer in ({@code supportedLanguages});
     * empty when the company never set them, so the deployment default applies.
     */
    List<String> findSupportedLanguageCodes(long companyId);

    CompanyConfig requireConfig(long companyId);

    CompanyConfig requireConfigForApi(long callerCompanyId, long companyId);

    CompanyConfig update(long callerCompanyId, long companyId, UpdateCompanyConfigRequest request);

    String resolveLanguage(long companyId, String requested);
}
