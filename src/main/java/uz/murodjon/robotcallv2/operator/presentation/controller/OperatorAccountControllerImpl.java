package uz.murodjon.robotcallv2.operator.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.operator.application.dto.*;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorFilter;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.shared.api.SseEventStreams;

@RestController
public class OperatorAccountControllerImpl implements OperatorAccountController {

    private final OperatorAccountUseCase operatorAccountUseCase;

    public OperatorAccountControllerImpl(OperatorAccountUseCase operatorAccountUseCase) {
        this.operatorAccountUseCase = operatorAccountUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<OperatorSipAccountRow>> saveSipAccount(long companyId, long userId,
                                                                              SaveOperatorSipAccountRequest request) {
        return ResponseEntity.ok(ResponseData.ok(operatorAccountUseCase.saveSipAccount(companyId, userId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<SipCredentialsResponse>> resetSipPassword(long companyId, long userId) {
        return ResponseEntity.ok(ResponseData.ok(operatorAccountUseCase.resetSipPassword(companyId, userId)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteSipAccount(long companyId, long userId) {
        operatorAccountUseCase.deleteSipAccount(companyId, userId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<OperatorRow>>> list(long companyId, OperatorFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(operatorAccountUseCase.listOperators(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<SipCredentialsResponse>> myCredentials(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(operatorAccountUseCase.findMyCredentials(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<OperatorPresence>> myStatus(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(operatorAccountUseCase.findMyStatus(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<OperatorPresence>> updateMyStatus(long companyId, UpdateOperatorStatusRequest request) {
        return ResponseEntity.ok(ResponseData.ok(operatorAccountUseCase.updateMyStatus(companyId, request)));
    }

    @Override
    public SseEmitter myEvents(long companyId) {
        return SseEventStreams.open(sink -> operatorAccountUseCase.subscribeMyEvents(companyId, sink));
    }
}
