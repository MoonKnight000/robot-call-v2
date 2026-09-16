package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The account a Payme payment is for — the fields configured on the Payme cashbox.
 *
 * @param paymentId the top-up's {@code paymentId}, e.g. {@code PAY-1A2B3C4D}
 */
public record PaymeAccount(
        @JsonProperty("payment_id") String paymentId
) {
}
