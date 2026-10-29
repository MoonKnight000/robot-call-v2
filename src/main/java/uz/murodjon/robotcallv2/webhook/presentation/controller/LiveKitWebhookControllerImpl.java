package uz.murodjon.robotcallv2.webhook.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookRequest;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookResponse;
import uz.murodjon.robotcallv2.webhook.application.port.input.LiveKitWebhookUseCase;

@RestController
public class LiveKitWebhookControllerImpl implements LiveKitWebhookController {

    private final LiveKitWebhookUseCase liveKitWebhookUseCase;

    public LiveKitWebhookControllerImpl(LiveKitWebhookUseCase liveKitWebhookUseCase) {
        this.liveKitWebhookUseCase = liveKitWebhookUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<LiveKitSipWebhookResponse>> handleSipWebhook(LiveKitSipWebhookRequest request) {
        return ResponseEntity.ok(ResponseData.ok(liveKitWebhookUseCase.handleSipWebhook(request)));
    }
}
