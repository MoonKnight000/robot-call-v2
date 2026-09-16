package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

    /** A summary nobody scored: no QA or commitment score rather than an invented 100/50. */
    public CallSummary(String summary, Map<String, Object> outcome, Sentiment sentiment,
                       boolean needsFollowUp, String followUpNote, String callbackAt) {
        this(summary, outcome, sentiment, needsFollowUp, followUpNote, callbackAt, null, null);
    }

    public CallSummary(String summary, Map<String, Object> outcome, Sentiment sentiment,
                       boolean needsFollowUp, String followUpNote) {
        this(summary, outcome, sentiment, needsFollowUp, followUpNote, null, null, null);
    }

    /** Outcome keys that hold a code, a date or an amount rather than words — kept by {@link #stripPersonalData}. */
    private static final Set<String> STRUCTURED_OUTCOME_KEYS = Set.of("reasonCode", "promisedDate", "promisedAmount");
    /** Where the agent's per-criterion verdicts live in {@code outcome}. */
    private static final String EVALUATION_KEY = "evaluation";

    public CallSummary {
        outcome = outcome == null ? Map.of() : Map.copyOf(outcome);
    }

    /**
     * The summary as a {@code zeroPiiRetention} agent's call may store it: the outcome
     * without the words. Kept — sentiment, the follow-up flag, the callback time, the scores,
     * the scenario's goal field, and from {@code outcome} the reason code, promised date and
     * promised amount and each evaluation criterion's verdict. Dropped — the summary text, the
     * follow-up note, every {@code dataNeeded} value and the reasons given for verdicts: text
     * the model wrote from the conversation, which is where a name, an address or a card
     * number ends up.
     *
     * <p>The goal field is kept because it is the one number the call exists to produce — an
     * appointment number, an order number (UNIVERSAL-OPERATOR-PLAN A.1) — and a clinic that
     * turns this on to keep the complaint out of its records would otherwise lose the booking
     * with it (E.3). It is a code by definition; a scenario whose goal field holds free text
     * is a scenario mislabelling its goal.
     *
     * @param goalField the scenario's goal field name, or null when it names none
     */
    public CallSummary stripPersonalData(String goalField) {
        Map<String, Object> kept = new HashMap<>();
        for (String key : STRUCTURED_OUTCOME_KEYS) {
            if (outcome.get(key) != null) {
                kept.put(key, outcome.get(key));
            }
        }
        if (goalField != null && outcome.get(goalField) != null) {
            kept.put(goalField, outcome.get(goalField));
        }
        if (outcome.get(EVALUATION_KEY) instanceof Map<?, ?> evaluation) {
            Map<String, Object> verdicts = new LinkedHashMap<>();
            evaluation.forEach((criterion, answer) -> {
                Object verdict = answer instanceof Map<?, ?> fields ? fields.get("verdict") : answer;
                verdicts.put(String.valueOf(criterion), Map.of("verdict", verdict != null ? verdict : "N/A"));
            });
            kept.put(EVALUATION_KEY, verdicts);
        }
        return new CallSummary("", kept, sentiment, needsFollowUp, null, callbackAt, qaScore, commitmentScore);
    }
}
