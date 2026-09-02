package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CallCostCalculatorTest {

    @Test
    void calculateZeroCostForZeroUsage() {
        var breakdown = CallCostCalculator.calculate(0, 0, 0, 0);

        assertThat(breakdown.promptTokens()).isZero();
        assertThat(breakdown.completionTokens()).isZero();
        assertThat(breakdown.totalTokens()).isZero();
        assertThat(breakdown.durationSeconds()).isZero();
        assertThat(breakdown.ttsCharacters()).isZero();
        assertThat(breakdown.llmCostUsd()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.sttCostUsd()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.ttsCostUsd()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.telephonyCostUsd()).isEqualByComparingTo("0.0000");
        assertThat(breakdown.totalCostUsd()).isEqualByComparingTo("0.0000");
    }

    @Test
    void calculateCalculatesLlmTokensAccurately() {
        // 1000 prompt tokens @ $0.00015 / 1k = $0.00015
        // 1000 completion tokens @ $0.00060 / 1k = $0.00060
        // Total LLM = 0.00075 -> rounded to 4 decimals = 0.0008
        var breakdown = CallCostCalculator.calculate(1000, 1000, 0, 0);

        assertThat(breakdown.totalTokens()).isEqualTo(2000);
        assertThat(breakdown.llmCostUsd()).isGreaterThan(BigDecimal.ZERO);
        assertThat(breakdown.totalCostUsd()).isEqualTo(breakdown.llmCostUsd());
    }

    @Test
    void calculateCalculatesDurationAndTtsCost() {
        // 60 seconds duration:
        // STT: 1.0 min * 0.006 = 0.0060
        // Telephony: 1.0 min * 0.015 = 0.0150
        // TTS: 1000 chars * 0.004 = 0.0040
        var breakdown = CallCostCalculator.calculate(0, 0, 60, 1000);

        assertThat(breakdown.durationSeconds()).isEqualTo(60);
        assertThat(breakdown.ttsCharacters()).isEqualTo(1000);
        assertThat(breakdown.sttCostUsd()).isEqualByComparingTo("0.0060");
        assertThat(breakdown.telephonyCostUsd()).isEqualByComparingTo("0.0150");
        assertThat(breakdown.ttsCostUsd()).isEqualByComparingTo("0.0040");
        assertThat(breakdown.totalCostUsd()).isEqualByComparingTo("0.0250");
    }

    @Test
    void calculateComputesFullCallCostBreakdown() {
        var breakdown = CallCostCalculator.calculate(2500, 800, 120, 1500);

        assertThat(breakdown.totalTokens()).isEqualTo(3300);
        assertThat(breakdown.durationSeconds()).isEqualTo(120);
        assertThat(breakdown.ttsCharacters()).isEqualTo(1500);
        assertThat(breakdown.totalCostUsd())
                .isEqualTo(breakdown.llmCostUsd()
                        .add(breakdown.sttCostUsd())
                        .add(breakdown.ttsCostUsd())
                        .add(breakdown.telephonyCostUsd()));
    }
}
