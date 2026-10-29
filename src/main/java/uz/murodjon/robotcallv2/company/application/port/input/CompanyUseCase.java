package uz.murodjon.robotcallv2.company.application.port.input;

import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyByPlatformRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyRequest;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;

import java.util.List;

public interface CompanyUseCase {

    Company create(CreateCompanyRequest request);

    Company update(long callerCompanyId, long id, UpdateCompanyRequest request);

    /** Moves every ACTIVE company past its end date to EXPIRED; for the scheduled job. */
    void expireLapsedCompanies();

    /** Platform-only: edits everything about the company in one go — profile, block/unblock/delete, end date, call limit, features. */
    Company updateByPlatform(long id, UpdateCompanyByPlatformRequest request);

    Company uploadLogo(long callerCompanyId, long id, UploadedFile file);

    PageableData<Company> list(CompanyFilter filter);

    Company findById(long id);

    /** Throws COMPANY_NOT_FOUND when there is no such company; for platform calls naming one by id. */
    void requireExists(long id);

    List<Company> findAllForWarmup();

    Company requireCompany(long callerCompanyId, long id);

    /**
     * For a user's request: refused with {@code COMPANY_SUSPENDED} when the company is closed, with
     * {@code COMPANY_FEATURE_DISABLED} when the platform has the feature off for it.
     */
    void requireFeatureEnabled(long companyId, Feature feature);

    /** {@link #requireFeatureEnabled} for {@code INTEGRATIONS}, then {@code COMPANY_INTEGRATION_DISABLED} for the provider. */
    void requireIntegrationEnabled(long companyId, IntegrationProvider provider);

    /** For calls, webhooks and background work, which skip rather than fail: {@link Company#mayUse(Feature)}. */
    boolean isFeatureAvailable(long companyId, Feature feature);

    /** {@link Company#mayUse(IntegrationProvider)}, for the same callers. */
    boolean isIntegrationAvailable(long companyId, IntegrationProvider provider);
}
