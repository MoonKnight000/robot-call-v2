package uz.murodjon.robotcallv2.crm.application.port.output;

import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;

import java.util.Optional;

public interface CrmSettingsRepository {

    Optional<CrmSettings> findByCompanyId(long companyId);

    CrmSettings upsert(long companyId, CrmSettings settings);
}
