package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One line of a Payme fiscal receipt.
 *
 * @param title       what the payer sees on the receipt
 * @param price       per unit, in tiyin
 * @param count       how many
 * @param code        the IKPU (MXIK) product code
 * @param packageCode the package code registered with that IKPU code
 * @param vatPercent  VAT percent
 */
public record PaymeReceiptItem(
        String title,
        long price,
        int count,
        String code,
        @JsonProperty("package_code") String packageCode,
        @JsonProperty("vat_percent") int vatPercent
) {
}
