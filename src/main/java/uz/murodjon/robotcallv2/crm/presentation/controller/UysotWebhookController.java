package uz.murodjon.robotcallv2.crm.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uz.murodjon.robotcallv2.crm.application.dto.UysotLeadWebhookEvent;
import uz.murodjon.robotcallv2.crm.application.dto.UysotWebhookResult;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Public webhook endpoint for inbound Uysot Open API events (docs/uysot-integration-guide.md).
 */
@RequestMapping("/api/public/uysot/webhook")
public interface UysotWebhookController {

    @PostMapping
    ResponseEntity<ResponseData<UysotWebhookResult>> handleWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestParam(value = "campaignId", required = false) Long campaignId,
            @Valid @RequestBody UysotLeadWebhookEvent event);
}
