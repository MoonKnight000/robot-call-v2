package uz.murodjon.robotcallv2.crm.application.port.input;

import uz.murodjon.robotcallv2.crm.application.dto.UysotLeadWebhookEvent;
import uz.murodjon.robotcallv2.crm.application.dto.UysotWebhookResult;

/**
 * Handles incoming webhooks from Uysot Open API (e.g. instant lead dispatch).
 */
public interface UysotWebhookUseCase {

    /** Queues the lead into the company's first ACTIVE AI campaign. */
    default UysotWebhookResult handleWebhook(UysotLeadWebhookEvent event, String secret) {
        return handleWebhook(event, secret, null);
    }

    /** Queues the lead into {@code campaignId} when given; it must be ACTIVE and place AI calls. */
    UysotWebhookResult handleWebhook(UysotLeadWebhookEvent event, String secret, Long campaignId);
}
