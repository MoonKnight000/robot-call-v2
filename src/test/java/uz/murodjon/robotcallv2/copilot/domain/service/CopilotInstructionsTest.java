package uz.murodjon.robotcallv2.copilot.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotPageContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotInstructionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Test
    void theInstructionsFormatWithTodaysDateAndKeepTheAnalysisRules() {
        String text = CopilotInstructions.forQuestion(TODAY, null);

        assertThat(text).contains("2026-11-04").contains("CALL ANALYSIS").contains("get_call_stats")
                .contains("9.1% -> 21.4%").contains("SCOPE").doesNotContain("CURRENT PAGE");
    }

    @Test
    void thePageTheUserAskedFromIsNamedWithItsIds() {
        String text = CopilotInstructions.forQuestion(TODAY,
                new CopilotPageContext("agent-editor", 12L, null, null, null));

        assertThat(text).contains("CURRENT PAGE").contains("\"agent-editor\"").contains("agent ID 12")
                .doesNotContain("campaign ID");
    }

    @Test
    void anEmptyContextAddsNothing() {
        assertThat(CopilotInstructions.forQuestion(TODAY, new CopilotPageContext(null, null, null, null, null)))
                .doesNotContain("CURRENT PAGE");
    }
}
