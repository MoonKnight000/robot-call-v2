package uz.murodjon.robotcallv2.callflow.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.callflow.application.dto.CallFlowValidationResponse;
import uz.murodjon.robotcallv2.callflow.application.dto.SaveCallFlowRequest;
import uz.murodjon.robotcallv2.callflow.application.port.input.CallFlowUseCase;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlow;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CallFlowControllerImpl implements CallFlowController {

    private final CallFlowUseCase callFlowUseCase;

    public CallFlowControllerImpl(CallFlowUseCase callFlowUseCase) {
        this.callFlowUseCase = callFlowUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CallFlow>> create(long companyId, SaveCallFlowRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callFlowUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallFlow>>> list(long companyId, CallFlowFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(callFlowUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallFlow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(callFlowUseCase.requireFlow(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<CallFlow>> update(long companyId, long id, SaveCallFlowRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callFlowUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        callFlowUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<CallFlowValidationResponse>> validate(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(callFlowUseCase.validateDraft(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<CallFlow>> publish(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(callFlowUseCase.publish(companyId, id)));
    }
}
