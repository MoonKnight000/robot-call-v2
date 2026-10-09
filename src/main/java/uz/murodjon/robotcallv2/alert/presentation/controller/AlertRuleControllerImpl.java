package uz.murodjon.robotcallv2.alert.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.alert.application.dto.AlertRulePreviewResponse;
import uz.murodjon.robotcallv2.alert.application.dto.SaveAlertRuleRequest;
import uz.murodjon.robotcallv2.alert.application.port.input.AlertRuleUseCase;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRuleFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class AlertRuleControllerImpl implements AlertRuleController {

    private final AlertRuleUseCase alertRuleUseCase;

    public AlertRuleControllerImpl(AlertRuleUseCase alertRuleUseCase) {
        this.alertRuleUseCase = alertRuleUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<AlertRule>> create(long companyId, SaveAlertRuleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(alertRuleUseCase.createRule(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AlertRule>>> filter(long companyId, AlertRuleFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(alertRuleUseCase.filterRules(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<AlertRule>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(alertRuleUseCase.findRule(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AlertRule>> update(long companyId, long id, SaveAlertRuleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(alertRuleUseCase.updateRule(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        alertRuleUseCase.deleteRule(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<AlertRulePreviewResponse>> preview(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(alertRuleUseCase.previewRule(companyId, id)));
    }
}
