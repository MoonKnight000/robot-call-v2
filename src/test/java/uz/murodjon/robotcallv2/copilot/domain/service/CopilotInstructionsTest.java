package uz.murodjon.robotcallv2.copilot.domain.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotInstructionsTest {

    @Test
    void theInstructionsFormatWithTodaysDateAndKeepTheAnalysisRules() {
        String text = CopilotInstructions.forDate(LocalDate.of(2026, 11, 4));

        assertThat(text).contains("2026-11-04").contains("CALL ANALYSIS").contains("get_call_stats")
                .contains("9.1% -> 21.4%");
    }
}
