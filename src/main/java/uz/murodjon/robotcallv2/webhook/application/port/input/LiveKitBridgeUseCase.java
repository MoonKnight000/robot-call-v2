package uz.murodjon.robotcallv2.webhook.application.port.input;

/**
 * What the Go media bridge tells the app about a LiveKit call.
 */
public interface LiveKitBridgeUseCase {

    /**
     * Verifies the report against the shared API secret, then hands the key the caller pressed to the
     * call (a flow's menu, a queue's confirmation, or the dialog).
     *
     * @param signature the request's {@code X-Bridge-Signature} header
     * @param body      the raw body, exactly as signed
     */
    void handleDtmf(String signature, byte[] body);
}
