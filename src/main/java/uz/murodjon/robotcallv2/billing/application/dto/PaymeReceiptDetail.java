package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The fiscal receipt Payme prints for the payment.
 *
 * @param receiptType 0 — a sale
 * @param items       what was sold
 */
public record PaymeReceiptDetail(
        @JsonProperty("receipt_type") int receiptType,
        List<PaymeReceiptItem> items
) {
}
