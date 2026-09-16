package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.PaymeResponse;

/** The Payme Merchant API (JSON-RPC 2.0), as Payme's servers call it. */
public interface PaymeMerchantUseCase {

    /**
     * Authenticates, parses and answers one Merchant API call.
     *
     * <p>Never throws for anything Payme sent: every refusal — bad credentials, a body that
     * is not JSON, an unknown method, a top-up that cannot be paid — comes back as a
     * JSON-RPC {@code error}, because Payme reads the outcome from the body.
     *
     * @param authorization the raw {@code Authorization} header, null when absent
     * @param body          the raw request body, null when empty
     */
    PaymeResponse handle(String authorization, String body);
}
