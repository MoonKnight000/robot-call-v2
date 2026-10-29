package uz.murodjon.robotcallv2.crm.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmSettingsRequest;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmSettingsUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CrmSettingsControllerImpl implements CrmSettingsController {

    private final CrmSettingsUseCase crmSettingsUseCase;

    public CrmSettingsControllerImpl(CrmSettingsUseCase crmSettingsUseCase) {
        this.crmSettingsUseCase = crmSettingsUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CrmSettings>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmSettingsUseCase.findByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<CrmSettings>> update(long companyId, UpdateCrmSettingsRequest request) {
        return ResponseEntity.ok(ResponseData.ok(crmSettingsUseCase.updateByCompanyId(companyId, request)));
    }
}
