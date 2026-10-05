package uz.murodjon.robotcallv2.crm.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.PromptSafeText;
import uz.murodjon.robotcallv2.crm.application.dto.UysotLeadAttributionDto;
import uz.murodjon.robotcallv2.crm.application.dto.UysotLeadPayloadDto;
import uz.murodjon.robotcallv2.crm.application.dto.UysotLeadWebhookEvent;
import uz.murodjon.robotcallv2.crm.application.dto.UysotWebhookResult;
import uz.murodjon.robotcallv2.crm.application.port.input.UysotWebhookUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallRequest;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.InstantCallUseCase;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Inbound webhook processor for Uysot Open API events.
 * Triggers instant outbound calls (&lt;10s speed-to-lead) when new leads arrive.
 */
@Service
public class UysotWebhookService implements UysotWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(UysotWebhookService.class);
    private static final String EVENT_LEAD_CREATED = "LEAD_CREATED";

    private final InstantCallUseCase instantCallUseCase;
    private final SecretUseCase secretUseCase;

    public UysotWebhookService(InstantCallUseCase instantCallUseCase, SecretUseCase secretUseCase) {
        this.instantCallUseCase = instantCallUseCase;
        this.secretUseCase = secretUseCase;
    }

    @Override
    public UysotWebhookResult handleWebhook(UysotLeadWebhookEvent event, String secret) {
        if (event == null) {
            return UysotWebhookResult.skipped(null, null, "Webhook payload is empty");
        }

        String eventId = event.eventId();
        String eventType = event.eventType();
        long companyId = event.companyId() != null ? event.companyId() : 0L;

        if (companyId <= 0) {
            log.warn("Uysot webhook {} (event={}) missing valid companyId: {}", eventId, eventType, companyId);
            return UysotWebhookResult.skipped(eventId, eventType, "Missing or invalid companyId");
        }

        Map<String, String> secrets = secretUseCase.findDecryptedSecrets(companyId);
        String expectedSecret = secrets.get("UYSOT_WEBHOOK_SECRET");
        if (expectedSecret == null) {
            expectedSecret = secrets.get("CRM_WEBHOOK_SECRET");
        }
        if (expectedSecret == null) {
            expectedSecret = secrets.get("WEBHOOK_SECRET");
        }
        if (expectedSecret != null && !expectedSecret.isBlank()) {
            if (secret == null || !MessageDigest.isEqual(expectedSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Uysot webhook {} for company {} rejected: invalid or missing X-Webhook-Secret", eventId, companyId);
                return UysotWebhookResult.skipped(eventId, eventType, "Unauthorized: invalid webhook secret");
            }
        }

        if (!EVENT_LEAD_CREATED.equalsIgnoreCase(eventType)) {
            log.debug("Uysot webhook {} eventType {} skipped (only LEAD_CREATED handled for instant call)", eventId, eventType);
            return UysotWebhookResult.skipped(eventId, eventType, "Event type " + eventType + " ignored");
        }

        UysotLeadPayloadDto lead = event.lead();
        if (lead == null) {
            log.warn("Uysot webhook {} has no lead payload", eventId);
            return UysotWebhookResult.skipped(eventId, eventType, "Lead payload is missing");
        }

        String phone = lead.phone();
        if (phone == null || phone.isBlank()) {
            log.warn("Uysot webhook {} lead id={} has no phone number", eventId, lead.id());
            return UysotWebhookResult.skipped(eventId, eventType, "Lead has no phone number");
        }

        String sanitizedName = lead.name() != null ? PromptSafeText.sanitize(lead.name().trim(), 100) : null;
        Map<String, Object> contextData = buildContext(lead);

        try {
            InstantCallRequest instantCallRequest = new InstantCallRequest(
                    null,
                    phone.trim(),
                    sanitizedName,
                    contextData,
                    lead.id()
            );

            InstantCallResponse response = instantCallUseCase.trigger(companyId, instantCallRequest);
            log.info("Uysot speed-to-lead triggered for lead id={} name='{}' phone={} targetId={}",
                    lead.id(), sanitizedName, PhoneMasker.mask(phone), response.targetId());

            return UysotWebhookResult.success(eventId, eventType, response.targetId(), "Instant call queued successfully");
        } catch (Exception e) {
            log.error("Failed to trigger instant call for Uysot webhook {} lead id={}: {}",
                    eventId, lead.id(), e.getMessage(), e);
            return UysotWebhookResult.skipped(eventId, eventType, "Failed to queue call");
        }
    }

    private Map<String, Object> buildContext(UysotLeadPayloadDto lead) {
        Map<String, Object> context = new HashMap<>();
        if (lead.id() != null) {
            context.put("leadId", lead.id());
        }
        if (lead.name() != null) {
            context.put("clientName", PromptSafeText.sanitize(lead.name().trim(), 100));
        }
        if (lead.statusId() != null) {
            context.put("statusId", lead.statusId());
        }
        if (lead.statusName() != null) {
            context.put("statusName", PromptSafeText.sanitize(lead.statusName().trim(), 100));
        }
        if (lead.pipeId() != null) {
            context.put("pipeId", lead.pipeId());
        }
        if (lead.pipeName() != null) {
            context.put("pipeName", PromptSafeText.sanitize(lead.pipeName().trim(), 100));
        }
        if (lead.responsibleId() != null) {
            context.put("responsibleId", lead.responsibleId());
        }
        if (lead.responsibleName() != null) {
            context.put("responsibleName", PromptSafeText.sanitize(lead.responsibleName().trim(), 100));
        }
        if (lead.currency() != null) {
            context.put("currency", PromptSafeText.sanitize(lead.currency().trim(), 10));
        }
        if (lead.balance() != null) {
            context.put("balance", lead.balance());
        }

        if (lead.attributions() != null && !lead.attributions().isEmpty()) {
            UysotLeadAttributionDto attr = lead.attributions().get(0);
            if (attr.source() != null) {
                context.put("source", PromptSafeText.sanitize(attr.source().trim(), 100));
            }
            if (attr.channelValue() != null) {
                context.put("channelValue", PromptSafeText.sanitize(attr.channelValue().trim(), 100));
            }
        }
        return context;
    }
}
