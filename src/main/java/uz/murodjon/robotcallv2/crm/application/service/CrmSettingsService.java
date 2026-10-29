package uz.murodjon.robotcallv2.crm.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmSettingsRequest;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmSettingsUseCase;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmSettingsRepository;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;

/** One company's CRM settings (docs/api/crm-settings.md). */
@Service
public class CrmSettingsService implements CrmSettingsUseCase {

    private final CrmSettingsRepository repository;
    private final AuditUseCase auditUseCase;

    public CrmSettingsService(CrmSettingsRepository repository, AuditUseCase auditUseCase) {
        this.repository = repository;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public CrmSettings findByCompanyId(long companyId) {
        return repository.findByCompanyId(companyId)
                .orElseGet(() -> new CrmSettings(companyId, null, null, null, null, null, null, null, null));
    }

    @Override
    public CrmSettings updateByCompanyId(long companyId, UpdateCrmSettingsRequest request) {
        CrmSettings saved = repository.upsert(companyId, CrmSettings.values(request.aiEmployeeId(),
                request.meetingBookedStatusId(), request.promiseToPayStatusId(), request.notInterestedStatusId(),
                request.callbackStatusId(), request.newLeadStatusId(), request.taskTypeId()));
        auditUseCase.record(companyId, "CRM_SETTINGS_UPDATE", "crm_settings", String.valueOf(companyId), null);
        return saved;
    }
}
