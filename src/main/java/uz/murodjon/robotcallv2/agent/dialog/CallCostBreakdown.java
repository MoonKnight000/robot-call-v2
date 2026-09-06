package uz.murodjon.robotcallv2.agent.dialog;

import java.math.BigDecimal;

/** What one call cost, split by the service that billed for it. */
public record CallCostBreakdown(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int durationSeconds,
        int ttsCharacters,
        BigDecimal llmCostUsd,
        BigDecimal sttCostUsd,
        BigDecimal ttsCostUsd,
        BigDecimal telephonyCostUsd,
        BigDecimal totalCostUsd
) {
}
