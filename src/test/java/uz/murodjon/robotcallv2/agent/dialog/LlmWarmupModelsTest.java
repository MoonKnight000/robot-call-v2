package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Which models a call's warm-up is sent on ({@link LlmTurnClient#warmupModels}). */
class LlmWarmupModelsTest {

    @Test
    void warmsTheMainAndTheFastModelWhenTheyDiffer() {
        assertThat(LlmTurnClient.warmupModels("gemini-3.8-flash", "gemini-3.5-flash-lite", true))
                .containsExactly(null, "gemini-3.5-flash-lite");
    }

    @Test
    void warmsTheMainModelOnlyWhenTheFastOneIsTheSame() {
        assertThat(LlmTurnClient.warmupModels("gemini-3.8-flash", "gemini-3.8-flash", true))
                .containsExactly((String) null);
    }

    @Test
    void warmsTheMainModelOnlyWithoutAFastOne() {
        assertThat(LlmTurnClient.warmupModels("gemini-3.8-flash", null, true)).containsExactly((String) null);
        assertThat(LlmTurnClient.warmupModels("gemini-3.8-flash", " ", true)).containsExactly((String) null);
    }

    @Test
    void leavesTheFastModelColdWhenSwitchedOff() {
        assertThat(LlmTurnClient.warmupModels("gemini-3.8-flash", "gemini-3.5-flash-lite", false))
                .containsExactly((String) null);
    }
}
