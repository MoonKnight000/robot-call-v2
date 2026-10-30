package uz.murodjon.robotcallv2.webhook.application.port.input;

/**
 * Webhooks from the LiveKit server (rooms and participants of LiveKit-engine calls).
 */
public interface LiveKitWebhookUseCase {

    /**
     * Verifies the event against the server's API secret, then acts on the phone legs in it:
     * an inbound caller joining is routed through the inbound rules (or hung up), an
     * outbound callee leaving settles the attempt.
     *
     * @param authorization the request's {@code Authorization} header (LiveKit's signed token)
     * @param body          the raw body, exactly as signed
     */
    void handleEvent(String authorization, byte[] body);
}
