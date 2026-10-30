package uz.murodjon.robotcallv2.webhook.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Public endpoint the Go media bridge reports to. No Bearer login: the body is authenticated by
 * the HMAC the bridge signs it with, using the LiveKit API secret both share.
 */
@RequestMapping("/api/webhooks/livekit-bridge")
public interface LiveKitBridgeController {

    /**
     * Receives one key the caller pressed. The body is read as raw bytes because the signature
     * covers them exactly.
     *
     * @return 200 once handled; 403 when the signature does not check out; 400 for a malformed report
     */
    @PostMapping("/dtmf")
    ResponseEntity<ResponseData<Void>> handleDtmf(
            @RequestHeader(value = "X-Bridge-Signature", required = false) String signature,
            @RequestBody byte[] body);
}
