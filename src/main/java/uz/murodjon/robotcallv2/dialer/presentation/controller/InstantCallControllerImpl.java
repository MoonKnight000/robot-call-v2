package uz.murodjon.robotcallv2.dialer.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallRequest;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.InstantCallUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class InstantCallControllerImpl implements InstantCallController {

    private final InstantCallUseCase instantCallUseCase;

    public InstantCallControllerImpl(InstantCallUseCase instantCallUseCase) {
        this.instantCallUseCase = instantCallUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<InstantCallResponse>> trigger(long companyId, InstantCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(instantCallUseCase.trigger(companyId, request)));
    }
}
