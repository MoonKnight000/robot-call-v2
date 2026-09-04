package uz.murodjon.robotcallv2.billing.application.dto;

public record BillingMetricsDto(
        BillingMetricItemDto minutes,
        BillingMetricItemDto aiTokens,
        BillingMetricItemDto ttsCharacters,
        BillingMetricItemDto concurrentChannels
) {
}
