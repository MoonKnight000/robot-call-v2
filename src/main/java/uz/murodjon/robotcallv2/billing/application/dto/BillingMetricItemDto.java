package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BillingMetricItemDto(
        long used,
        long limit,
        String unit,
        Double overagePricePerUnit
) {
}
