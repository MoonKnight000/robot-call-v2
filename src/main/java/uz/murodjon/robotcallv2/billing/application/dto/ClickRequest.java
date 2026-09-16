package uz.murodjon.robotcallv2.billing.application.dto;

import java.util.Map;

/**
 * One Click SHOP API callback, every value kept exactly as Click posted it: the sign is
 * computed over the raw strings, so {@code amount} has to stay {@code "1000.00"} rather than
 * be parsed and printed back.
 *
 * @param merchantTransId   our top-up's {@code paymentId} (the {@code transaction_param} of the pay link)
 * @param merchantPrepareId our transaction id from Prepare; Complete only
 * @param amount            in UZS, possibly with decimals
 * @param action            0 Prepare, 1 Complete
 * @param error             Click's own outcome; below 0 on Complete means the payment failed
 */
public record ClickRequest(
        String clickTransId,
        String serviceId,
        String merchantTransId,
        String merchantPrepareId,
        String amount,
        String action,
        String error,
        String errorNote,
        String signTime,
        String signString
) {
    public static ClickRequest fromForm(Map<String, String> form) {
        return new ClickRequest(
                form.get("click_trans_id"),
                form.get("service_id"),
                form.get("merchant_trans_id"),
                form.get("merchant_prepare_id"),
                form.get("amount"),
                form.get("action"),
                form.get("error"),
                form.get("error_note"),
                form.get("sign_time"),
                form.get("sign_string"));
    }
}
