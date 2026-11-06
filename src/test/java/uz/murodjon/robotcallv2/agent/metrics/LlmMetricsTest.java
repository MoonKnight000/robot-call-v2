package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The prompt budget only reports — it must count a turn over it once and never anything else. */
class LlmMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LlmMetrics metrics = new LlmMetrics(registry);

    private double overBudget() {
        return registry.get("voice.llm.prompt.over.budget").counter().count();
    }

    @Test
    void aTurnOverTheBudgetIsReportedAndCounted() {
        assertThat(metrics.recordTurnPrompt(21000, 12500, 12000)).isTrue();

        assertThat(overBudget()).isEqualTo(1);
        assertThat(registry.get("voice.llm.prompt.tokens").summary().max()).isEqualTo(12500);
        assertThat(registry.get("voice.llm.prompt.system.chars").summary().max()).isEqualTo(21000);
    }

    @Test
    void aTurnAtOrUnderTheBudgetIsNot() {
        assertThat(metrics.recordTurnPrompt(15000, 12000, 12000)).isFalse();
        assertThat(metrics.recordTurnPrompt(15000, 7400, 12000)).isFalse();

        assertThat(overBudget()).isZero();
    }

    @Test
    void noBudgetOrNoReportedTokensNeverCounts() {
        assertThat(metrics.recordTurnPrompt(15000, 50000, 0)).isFalse();
        assertThat(metrics.recordTurnPrompt(15000, 0, 12000)).isFalse();

        assertThat(overBudget()).isZero();
        assertThat(registry.get("voice.llm.prompt.tokens").summary().count()).isEqualTo(1);
    }
}
