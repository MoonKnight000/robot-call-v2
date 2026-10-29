package uz.murodjon.robotcallv2.webhook.application.port.input;

import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookRequest;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookResponse;

/**
 * Inbound webhook processor for LiveKit SIP Gateway dispatch events.
 */
public interface LiveKitWebhookUseCase {

    /**
     * Evaluates an inbound LiveKit SIP call against company inbound routes and rules,
     * returning room routing or rejection instructions.
     */
    LiveKitSipWebhookResponse handleSipWebhook(LiveKitSipWebhookRequest request);
}
