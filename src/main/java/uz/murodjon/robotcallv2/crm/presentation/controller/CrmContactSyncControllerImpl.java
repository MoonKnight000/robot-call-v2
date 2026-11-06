package uz.murodjon.robotcallv2.crm.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.crm.application.dto.CrmContactSyncResponse;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmContactSyncRequest;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmContactSyncUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmFieldOption;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class CrmContactSyncControllerImpl implements CrmContactSyncController {

    private final CrmContactSyncUseCase crmContactSyncUseCase;

    public CrmContactSyncControllerImpl(CrmContactSyncUseCase crmContactSyncUseCase) {
        this.crmContactSyncUseCase = crmContactSyncUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CrmContactSyncResponse>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmContactSyncUseCase.findContactSync(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<CrmContactSyncResponse>> update(long companyId, UpdateCrmContactSyncRequest request) {
        return ResponseEntity.ok(ResponseData.ok(crmContactSyncUseCase.updateContactSync(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CrmFieldOption>>> crmFields(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmContactSyncUseCase.findCrmContactFields(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<CrmContactSyncResponse>> run(long companyId, boolean full) {
        return ResponseEntity.ok(ResponseData.ok(crmContactSyncUseCase.requestContactSyncRun(companyId, full)));
    }
}
