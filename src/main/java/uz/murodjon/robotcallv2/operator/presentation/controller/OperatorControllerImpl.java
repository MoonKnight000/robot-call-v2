package uz.murodjon.robotcallv2.operator.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.operator.application.dto.OperatorContextResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorJoinResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorTakeoverResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperRequest;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperResponse;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class OperatorControllerImpl implements OperatorController {

    private final OperatorUseCase service;

    public OperatorControllerImpl(OperatorUseCase service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<OperatorContextResponse>> context(long companyId, String channelId) {
        return ResponseEntity.ok(ResponseData.ok(service.findContext(companyId, channelId)));
    }

    @Override
    public ResponseEntity<ResponseData<OperatorTakeoverResponse>> takeover(long companyId, String channelId,
                                                                           String extension) {
        return ResponseEntity.ok(ResponseData.ok(service.takeover(companyId, channelId, extension)));
    }

    @Override
    public ResponseEntity<ResponseData<OperatorWhisperResponse>> whisper(long companyId, String channelId,
                                                                         OperatorWhisperRequest request) {
        return ResponseEntity.ok(ResponseData.ok(service.whisper(companyId, channelId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<OperatorJoinResponse>> accept(long companyId, String channelId) {
        return ResponseEntity.ok(ResponseData.ok(service.acceptTransfer(companyId, channelId)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> decline(long companyId, String channelId) {
        service.rejectTransfer(companyId, channelId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
