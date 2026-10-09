package uz.murodjon.robotcallv2.wrapup.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.wrapup.application.dto.WrapUpCallRequest;
import uz.murodjon.robotcallv2.wrapup.application.port.input.WrapUpUseCase;
import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;

@RestController
public class CallWrapUpControllerImpl implements CallWrapUpController {

    private final WrapUpUseCase wrapUpUseCase;

    public CallWrapUpControllerImpl(WrapUpUseCase wrapUpUseCase) {
        this.wrapUpUseCase = wrapUpUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CallWrapUp>> wrapUp(long companyId, long callId, WrapUpCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(wrapUpUseCase.recordCallWrapUp(companyId, callId, request)));
    }
}
