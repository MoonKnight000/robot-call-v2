package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.util.Map;

/**
 * Structured summary produced by a single LLM call over the full transcript after
 * the conversation ends (PROJECT.md §4.3, MASTER_ROADMAP.md §10). Written to {@code call_result}
 * and to the CRM note.
 *
 * @param summary          2–3 sentence CRM note
 * @param outcome          the scenario's own {@code outcomeSchema} fields, by name (e.g.
 *                         {@code reasonCode}/{@code promisedDate}/{@code promisedAmount})
 * @param sentiment        overall client sentiment (POSITIVE, NEUTRAL, NEGATIVE, HOSTILE)
 * @param needsFollowUp    whether a follow-up call is warranted
 * @param followUpNote     note for the follow-up (nullable)
 * @param callbackAt       scheduled callback timestamp (ISO 8601, nullable)
 * @param qaScore          Automated AI Quality Score (0-100) evaluating bot politeness & script adherence
 * @param commitmentScore  Client's willingness/commitment level to pay (0-100)
 */
public record CallSummary(
        String summary,
        Map<String, Object> outcome,
        Sentiment sentiment,
        boolean needsFollowUp,
        String followUpNote,
        String callbackAt,
        Integer qaScore,
        Integer commitmentScore
) {

    public CallSummary(String summary, Map<String, Object> outcome, Sentiment sentiment,
                       boolean needsFollowUp, String followUpNote, String callbackAt) {
        this(summary, outcome, sentiment, needsFollowUp, followUpNote, callbackAt, 100, 50);
    }

    public CallSummary(String summary, Map<String, Object> outcome, Sentiment sentiment,
                       boolean needsFollowUp, String followUpNote) {
        this(summary, outcome, sentiment, needsFollowUp, followUpNote, null, 100, 50);
    }

    public CallSummary {
        outcome = outcome == null ? Map.of() : Map.copyOf(outcome);
    }
}
