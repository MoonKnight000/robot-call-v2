package uz.murodjon.robotcallv2.agent.dialog;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facts about the call's subject, injected into the system prompt (PROJECT.md §4.4,
 * ROADMAP A.3). These are ground truth — the LLM states them but must never alter
 * them. Keyed by the bound scenario's {@code ScenarioDefinition.factSchema()} names
 * (e.g. {@code clientName}, {@code debtAmount}), so the shape varies per scenario.
 * Populated from campaign target {@code context_data} (Stage 10) or {@link
 * TestContextProperties} for manual/test calls.
 *
 * <p>Every text value is put through {@link PromptSafeText} on the way in. The values
 * come from a CRM record and from a CSV somebody uploaded, and they are pasted into the
 * prompt beside the rules the model has to follow — so this is the boundary where text
 * this system did not write stops being able to look like an instruction. Doing it here
 * rather than at each prompt factory means a new consumer cannot forget.
 *
 * @param facts fact name -> value ({@link String}, {@link java.math.BigDecimal}, or
 *              {@link java.time.LocalDate}, matching the fact's declared type)
 * @param goal  campaign goal / extra instruction appended to the prompt (nullable) —
 *              deliberately not a fact: it is campaign-level, not part of any
 *              scenario's factSchema
 */
public record CallContext(Map<String, Object> facts, String goal) {

    /** A fact fills one line of the prompt: a name, a sum, a date, a contract number. */
    private static final int MAX_FACT_CHARS = 200;

    /** The goal is a sentence or two of campaign instruction, so it gets more room. */
    private static final int MAX_GOAL_CHARS = 500;

    public CallContext {
        facts = sanitize(facts);
        goal = PromptSafeText.sanitize(goal, MAX_GOAL_CHARS);
    }

    /**
     * Only {@link String} values are touched. A {@code BigDecimal} or {@code LocalDate}
     * has already been parsed into a shape that cannot carry a sentence, and turning one
     * into text here would lose the type the fact schema declared.
     */
    private static Map<String, Object> sanitize(Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>(facts.size());
        facts.forEach((name, value) -> safe.put(name,
                value instanceof String text ? PromptSafeText.sanitize(text, MAX_FACT_CHARS) : value));
        return Map.copyOf(safe);
    }

    /** Convenience lookup; {@code null} if this scenario has no such fact or it was never set. */
    public Object fact(String name) {
        return facts.get(name);
    }
}
