package uz.murodjon.robotcallv2.webhook.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.webhook.application.port.input.LiveKitWebhookUseCase;

@RestController
public class LiveKitWebhookControllerImpl implements LiveKitWebhookController {

    private final LiveKitWebhookUseCase liveKitWebhookUseCase;

    public LiveKitWebhookControllerImpl(LiveKitWebhookUseCase liveKitWebhookUseCase) {
        this.liveKitWebhookUseCase = liveKitWebhookUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<Void>> handleEvent(String authorization, byte[] body) {
        liveKitWebhookUseCase.handleEvent(authorization, body);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
