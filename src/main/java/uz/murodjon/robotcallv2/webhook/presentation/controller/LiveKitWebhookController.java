package uz.murodjon.robotcallv2.webhook.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookRequest;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookResponse;

/**
 * Public webhook endpoint for inbound LiveKit SIP Gateway dispatch events.
 */
@RequestMapping("/api/webhooks/livekit")
public interface LiveKitWebhookController {

    @PostMapping("/sip")
    ResponseEntity<ResponseData<LiveKitSipWebhookResponse>> handleSipWebhook(
            @Valid @RequestBody LiveKitSipWebhookRequest request);
}
