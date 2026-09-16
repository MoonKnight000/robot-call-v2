package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CheckPerformTransaction's answer.
 *
 * @param allow  always true — a refusal is an error, not {@code allow: false}
 * @param detail the fiscal receipt; left out while no IKPU code is configured
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymeCheckPerformResult(
        boolean allow,
        PaymeReceiptDetail detail
) {
}
