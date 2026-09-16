package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.ClickResponse;

import java.util.Map;

/**
 * The Click SHOP API, as Click's servers call it. Neither method throws for anything Click
 * sent: every refusal comes back as Click's own negative {@code error} code.
 */
public interface ClickMerchantUseCase {

    /**
     * Prepare (action 0): may this top-up be paid? Opens a transaction on it.
     *
     * @param form the form fields exactly as Click posted them
     */
    ClickResponse prepare(Map<String, String> form);

    /**
     * Complete (action 1): the payment went through and the balance is credited — or, when
     * Click reports {@code error < 0}, it did not and the transaction is cancelled.
     *
     * @param form the form fields exactly as Click posted them
     */
    ClickResponse complete(Map<String, String> form);
}
