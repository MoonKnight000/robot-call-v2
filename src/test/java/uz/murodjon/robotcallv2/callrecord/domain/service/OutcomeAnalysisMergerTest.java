package uz.murodjon.robotcallv2.callrecord.domain.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeAnalysisMergerTest {

    private static final List<String> KEYS = List.of("amount", "evaluation", "extractionErrors");

    /** The summary's own fields are what reports and the CRM already show; they stay. */
    @Test
    void keysOutsideTheAnalysisAreKept() {
        Map<String, Object> stored = Map.of("reasonCode", "NO_MONEY", "promisedDate", "2026-10-20", "amount", 100);

        Map<String, Object> merged = OutcomeAnalysisMerger.mergeAnalysis(stored, Map.of("amount", 250), KEYS);

        assertThat(merged).containsEntry("reasonCode", "NO_MONEY")
                .containsEntry("promisedDate", "2026-10-20")
                .containsEntry("amount", 250);
    }

    /** A value the new run no longer finds must not linger from the old one. */
    @Test
    void anAnalysisKeyTheNewRunLeftOutIsRemoved() {
        Map<String, Object> stored = Map.of("amount", 100, "extractionErrors", List.of("amount"));

        Map<String, Object> merged = OutcomeAnalysisMerger.mergeAnalysis(stored, Map.of(), KEYS);

        assertThat(merged).doesNotContainKeys("amount", "extractionErrors");
    }

    /** The fresh outcome also carries scenario fields; only the analysis keys are taken from it. */
    @Test
    void freshKeysOutsideTheAnalysisAreIgnored() {
        Map<String, Object> merged = OutcomeAnalysisMerger.mergeAnalysis(Map.of("reasonCode", "NO_MONEY"),
                Map.of("reasonCode", "REFUSED", "evaluation", Map.of("polite", "PASS")), KEYS);

        assertThat(merged).containsEntry("reasonCode", "NO_MONEY")
                .containsEntry("evaluation", Map.of("polite", "PASS"));
    }
}
