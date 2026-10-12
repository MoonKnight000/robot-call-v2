package uz.murodjon.robotcallv2.webhook.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.webhook.application.dto.WebhookDeliveryRow;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDeliveryFilter;

/**
 * The post-call webhooks that did not get through on their first try: their retries, and a
 * button to send a {@code FAILED} one again once the receiver is fixed.
 */
@RequestMapping("/api/webhook-deliveries")
public interface WebhookDeliveryController {

    /** Newest first by default. Shows the host only — never the URL, headers or payload. */
    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping("/filter")
    ResponseEntity<ResponseData<PageableData<WebhookDeliveryRow>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody WebhookDeliveryFilter filter);

    /**
     * Sends a {@code FAILED} delivery once more, now, with its original
     * {@code X-RobotCall-Delivery} id. 200 either way — the row's {@code status} says whether
     * it got through; 409 {@code WEBHOOK_DELIVERY_NOT_FAILED} when it is not failed.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/resend")
    ResponseEntity<ResponseData<WebhookDeliveryRow>> resend(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /**
     * Real-time server-sent events stream of webhook delivery attempts for developers / local testing.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping(value = "/listen", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter listen(@CurrentCompanyId long companyId);
}
