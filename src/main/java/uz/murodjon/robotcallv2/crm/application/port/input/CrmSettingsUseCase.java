package uz.murodjon.robotcallv2.crm.application.port.input;

import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmSettingsRequest;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;

/** One company's CRM settings: outcome → pipeline stage, and the AI's employee id. */
public interface CrmSettingsUseCase {

    /** The saved settings, or an all-{@code null} row when the company saved none. */
    CrmSettings findByCompanyId(long companyId);

    CrmSettings updateByCompanyId(long companyId, UpdateCrmSettingsRequest request);
}
