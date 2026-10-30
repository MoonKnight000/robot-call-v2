package uz.murodjon.robotcallv2.webhook.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Public webhook endpoint the LiveKit server posts its room and participant events to
 * ({@code webhook.urls} in the LiveKit config). No Bearer login: the body is authenticated
 * by the token LiveKit signs with the API secret.
 */
@RequestMapping("/api/webhooks/livekit")
public interface LiveKitWebhookController {

    /**
     * Receives one {@code livekit.WebhookEvent}. The body is read as raw bytes because the
     * signature covers them exactly.
     *
     * @return 200 once handled; 403 when the signature does not check out
     */
    @PostMapping
    ResponseEntity<ResponseData<Void>> handleEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody byte[] body);
}
