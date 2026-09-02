package uz.murodjon.robotcallv2.agent.dialog;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates per-call resource usage and monetary cost across LLM tokens, STT duration, TTS characters and telephony.
 */
public final class CallCostCalculator {

    // Default rate benchmarks in USD per unit (customizable per company/tier)
    private static final BigDecimal LLM_INPUT_PER_1K = new BigDecimal("0.00015"); // e.g. Gemini Flash
    private static final BigDecimal LLM_OUTPUT_PER_1K = new BigDecimal("0.00060");
    private static final BigDecimal STT_PER_MINUTE = new BigDecimal("0.006"); // Yandex STT
    private static final BigDecimal TTS_PER_1K_CHARS = new BigDecimal("0.004"); // Yandex/Aisha TTS
    private static final BigDecimal TELEPHONY_PER_MINUTE = new BigDecimal("0.015"); // SIP trunk outbound

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

    private CallCostCalculator() {
    }

    /**
     * Calculates the estimated cost breakdown for a finished call.
     */
    public static CallCostBreakdown calculate(int promptTokens, int completionTokens,
                                             int durationSeconds, int ttsCharacters) {
        BigDecimal promptCost = BigDecimal.valueOf(promptTokens)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(LLM_INPUT_PER_1K);

        BigDecimal completionCost = BigDecimal.valueOf(completionTokens)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(LLM_OUTPUT_PER_1K);

        BigDecimal llmCost = promptCost.add(completionCost);

        BigDecimal durationMinutes = BigDecimal.valueOf(durationSeconds)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

        BigDecimal sttCost = durationMinutes.multiply(STT_PER_MINUTE);
        BigDecimal telephonyCost = durationMinutes.multiply(TELEPHONY_PER_MINUTE);

        BigDecimal ttsCost = BigDecimal.valueOf(ttsCharacters)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(TTS_PER_1K_CHARS);

        BigDecimal total = llmCost.add(sttCost).add(telephonyCost).add(ttsCost)
                .setScale(4, RoundingMode.HALF_UP);

        return new CallCostBreakdown(
                promptTokens,
                completionTokens,
                promptTokens + completionTokens,
                durationSeconds,
                ttsCharacters,
                llmCost.setScale(4, RoundingMode.HALF_UP),
                sttCost.setScale(4, RoundingMode.HALF_UP),
                ttsCost.setScale(4, RoundingMode.HALF_UP),
                telephonyCost.setScale(4, RoundingMode.HALF_UP),
                total
        );
    }
}
