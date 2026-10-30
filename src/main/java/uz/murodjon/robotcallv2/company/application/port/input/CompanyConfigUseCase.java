package uz.murodjon.robotcallv2.company.application.port.input;

import uz.murodjon.robotcallv2.company.application.dto.TelephonyEngineResponse;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyConfigRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateTelephonyEngineRequest;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;

public interface CompanyConfigUseCase {

    void createDefault(long companyId);

    CompanyConfig find(long companyId);

    CompanyConfig requireConfig(long companyId);

    CompanyConfig requireConfigForApi(long callerCompanyId, long companyId);

    CompanyConfig update(long callerCompanyId, long companyId, UpdateCompanyConfigRequest request);

    /** Platform-only: the engine the company's calls run on. */
    TelephonyEngineResponse findTelephonyEngine(long companyId);

    /** Platform-only: switches the engine the company's calls run on. */
    TelephonyEngineResponse updateTelephonyEngine(long companyId, UpdateTelephonyEngineRequest request);
}
