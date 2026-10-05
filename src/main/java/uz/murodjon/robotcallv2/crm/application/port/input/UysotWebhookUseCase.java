package uz.murodjon.robotcallv2.crm.application.port.input;

import uz.murodjon.robotcallv2.crm.application.dto.UysotLeadWebhookEvent;
import uz.murodjon.robotcallv2.crm.application.dto.UysotWebhookResult;

/**
 * Handles incoming webhooks from Uysot Open API (e.g. instant lead dispatch).
 */
public interface UysotWebhookUseCase {

    UysotWebhookResult handleWebhook(UysotLeadWebhookEvent event, String secret);
}
