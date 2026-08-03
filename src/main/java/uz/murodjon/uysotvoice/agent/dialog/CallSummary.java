package uz.murodjon.uysotvoice.agent.dialog;

import uz.murodjon.uysotvoice.shared.dialog.Sentiment;

import java.util.Map;

/**
 * Structured summary produced by a single LLM call over the full transcript after
 * the conversation ends (PROJECT.md §4.3, ROADMAP A.3). Written to {@code call_result}
 * and to the CRM note.
 *
 * @param summary       2–3 sentence CRM note
 * @param outcome       the scenario's own {@code outcomeSchema} fields, by name (e.g.
 *                      {@code reasonCode}/{@code promisedDate}/{@code promisedAmount}
 *                      for {@code debt-collection}, {@code answers}/{@code score} for
 *                      {@code survey}) — empty if the call yielded none
 * @param sentiment     overall client sentiment
 * @param needsFollowUp whether a follow-up call is warranted
 * @param followUpNote  note for the follow-up (nullable)
 */
public record CallSummary(
        String summary,
        Map<String, Object> outcome,
        Sentiment sentiment,
        boolean needsFollowUp,
        String followUpNote
) {

    public CallSummary {
        outcome = outcome == null ? Map.of() : Map.copyOf(outcome);
    }
}
