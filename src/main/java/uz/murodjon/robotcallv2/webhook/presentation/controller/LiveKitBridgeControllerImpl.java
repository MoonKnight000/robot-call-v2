package uz.murodjon.robotcallv2.webhook.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.webhook.application.port.input.LiveKitBridgeUseCase;

@RestController
public class LiveKitBridgeControllerImpl implements LiveKitBridgeController {

    private final LiveKitBridgeUseCase liveKitBridgeUseCase;

    public LiveKitBridgeControllerImpl(LiveKitBridgeUseCase liveKitBridgeUseCase) {
        this.liveKitBridgeUseCase = liveKitBridgeUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<Void>> handleDtmf(String signature, byte[] body) {
        liveKitBridgeUseCase.handleDtmf(signature, body);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
