package uz.murodjon.robotcallv2.insight.domain.entity;

import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.ArrayList;
import java.util.List;

/**
 * A window of one agent's calls, added up (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>What the daily job hands the clusterer, and deliberately all it hands it: counts, the
 * judge's one-line notes and the reviewers' reasons. Not the transcripts — a day of calls is
 * far more text than a model can weigh at once, and the two places that already read a
 * transcript (the judge, the reviewer) have each left behind the sentence that mattered.
 *
 * <p>Spring-free and built by a static factory, so the arithmetic under every proposal can be
 * tested without a model, a database or a call.
 */
public record AgentCallSignals(
        int calls,
        Integer avgScore,
        int guardrailViolations,
        int unansweredQuestions,
        int toolMisuses,
        int languageMismatches,
        int talkedOverClient,
        int toolErrorCalls,
        int repeatCalls,
        int transfers,
        int repairs,
        int noInputPrompts,
        int lowConfidence,
        List<String> judgeNotes,
        List<String> reviewerNotes,
        List<Long> sampleCalls
) {

    /** Enough notes to see a pattern; past this they repeat each other and only cost tokens. */
    private static final int MAX_NOTES = 40;

    /** The calls the inbox offers as evidence — a person opens two or three, never forty. */
    private static final int MAX_SAMPLE_CALLS = 10;

    public AgentCallSignals {
        judgeNotes = judgeNotes == null ? List.of() : List.copyOf(judgeNotes);
        reviewerNotes = reviewerNotes == null ? List.of() : List.copyOf(reviewerNotes);
        sampleCalls = sampleCalls == null ? List.of() : List.copyOf(sampleCalls);
    }

    public static AgentCallSignals of(List<CallQuality> rows) {
        int scored = 0;
        long scoreSum = 0;
        int guardrail = 0;
        int unanswered = 0;
        int toolMisuse = 0;
        int languageMismatch = 0;
        int talkedOver = 0;
        int toolErrors = 0;
        int repeats = 0;
        int transferred = 0;
        int repairCount = 0;
        int noInput = 0;
        int lowConfidenceCount = 0;
        List<String> judgeNotes = new ArrayList<>();
        List<String> reviewerNotes = new ArrayList<>();
        List<Long> samples = new ArrayList<>();
        for (CallQuality row : rows) {
            if (row.score() != null) {
                scored++;
                scoreSum += row.score();
            }
            guardrail += flag(row.guardrailViolation());
            unanswered += flag(row.unansweredQuestion());
            toolMisuse += flag(row.toolMisuse());
            languageMismatch += flag(row.languageMismatch());
            talkedOver += flag(row.talkedOverClient());
            toolErrors += row.toolErrorCount() > 0 ? 1 : 0;
            repeats += row.repeatCall() ? 1 : 0;
            transferred += row.disposition() == Disposition.TRANSFERRED ? 1 : 0;
            repairCount += row.repairCount();
            noInput += row.noInputPromptCount();
            lowConfidenceCount += row.lowConfidenceCount();
            if (row.note() != null && !row.note().isBlank() && judgeNotes.size() < MAX_NOTES) {
                judgeNotes.add(row.note().trim());
            }
            if (row.reviewStatus() == CallReviewStatus.AGENT_WRONG && row.reviewReason() != null
                    && reviewerNotes.size() < MAX_NOTES) {
                reviewerNotes.add(row.reviewReason().trim());
            }
            // The evidence is the calls something was wrong with, not the first ten of the day.
            if (isTroubled(row) && samples.size() < MAX_SAMPLE_CALLS) {
                samples.add(row.callAttemptId());
            }
        }
        return new AgentCallSignals(rows.size(), scored > 0 ? (int) (scoreSum / scored) : null,
                guardrail, unanswered, toolMisuse, languageMismatch, talkedOver, toolErrors, repeats,
                transferred, repairCount, noInput, lowConfidenceCount, judgeNotes, reviewerNotes, samples);
    }

    /**
     * Whether there is anything here worth a model's time. A day on which nothing went wrong
     * is the common case, and asking an LLM to find a pattern in it invents one.
     */
    public boolean worthClustering() {
        return guardrailViolations > 0 || unansweredQuestions > 0 || toolMisuses > 0
                || languageMismatches > 0 || talkedOverClient > 0 || toolErrorCalls > 0
                || repeatCalls > 0 || !reviewerNotes.isEmpty();
    }

    private static boolean isTroubled(CallQuality row) {
        return row.needsReview() || row.toolErrorCount() > 0 || row.repeatCall()
                || row.reviewStatus() == CallReviewStatus.AGENT_WRONG;
    }

    private static int flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }
}
