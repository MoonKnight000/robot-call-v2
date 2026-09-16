package uz.murodjon.robotcallv2.insight.domain.entity;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arithmetic every proposal rests on. A model is asked to explain these numbers, so a
 * number that is wrong here becomes a confident sentence in somebody's inbox.
 */
class AgentCallSignalsTest {

    @Test
    void countsWhatWentWrongAndKeepsTheSentencesThatSayWhy() {
        AgentCallSignals signals = AgentCallSignals.of(List.of(
                troubled(11L, true, "Chegirma va'da qildi"),
                clean(12L),
                toolBroken(13L)));

        assertThat(signals.calls()).isEqualTo(3);
        assertThat(signals.guardrailViolations()).isEqualTo(1);
        assertThat(signals.toolErrorCalls()).isEqualTo(1);
        assertThat(signals.repeatCalls()).isEqualTo(1);
        assertThat(signals.judgeNotes()).containsExactly("Chegirma va'da qildi");
        assertThat(signals.reviewerNotes()).containsExactly("Narxni o'zi to'qidi");
        // The evidence is the calls something happened on — the clean one is not offered.
        assertThat(signals.sampleCalls()).containsExactly(11L, 13L);
    }

    @Test
    void averagesOnlyTheCallsTheJudgeScored() {
        // The third call was never scored — off, not sampled, or the judge failed — and
        // counting it as a zero would report a collapse that did not happen.
        AgentCallSignals signals = AgentCallSignals.of(List.of(scored(21L, 80), scored(22L, 60), unscored(23L)));

        assertThat(signals.avgScore()).isEqualTo(70);
    }

    @Test
    void aDayOnWhichNothingWentWrongIsNotWorthAModel() {
        assertThat(AgentCallSignals.of(List.of(clean(31L), clean(32L))).worthClustering()).isFalse();
        assertThat(AgentCallSignals.of(List.of(clean(33L), toolBroken(34L))).worthClustering()).isTrue();
    }

    @Test
    void anAgentWithNoCallsAtAllSaysNothing() {
        AgentCallSignals signals = AgentCallSignals.of(List.of());

        assertThat(signals.calls()).isZero();
        assertThat(signals.avgScore()).isNull();
        assertThat(signals.worthClustering()).isFalse();
    }

    private static CallQuality clean(long callId) {
        return row(callId, 90, false, null, false, 0, false, null, null);
    }

    private static CallQuality scored(long callId, int score) {
        return row(callId, score, false, null, false, 0, false, null, null);
    }

    /** A call the judge never scored: every verdict field is null, the signals still stand. */
    private static CallQuality unscored(long callId) {
        return row(callId, null, false, null, false, 0, false, null, null);
    }

    private static CallQuality troubled(long callId, boolean guardrail, String note) {
        return row(callId, 40, guardrail, note, true, 0, false, null, null);
    }

    /** A call whose tool answered with an error, that the caller then rang back about. */
    private static CallQuality toolBroken(long callId) {
        return row(callId, 55, false, null, false, 2, true, CallReviewStatus.AGENT_WRONG, "Narxni o'zi to'qidi");
    }

    private static CallQuality row(long callId, Integer score, boolean guardrail, String note, boolean needsReview,
                                   int toolErrors, boolean repeatCall, CallReviewStatus reviewStatus,
                                   String reviewReason) {
        return new CallQuality(callId, 1L, callId, 7L, 1, Disposition.COMPLETED, score,
                guardrail, false, false, true, true, false, false, note, needsReview,
                6, 0, 0, 0, 0, toolErrors, repeatCall,
                reviewStatus, reviewReason, null, null, null, null, Instant.parse("2026-09-16T09:00:00Z"));
    }
}
