package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Map;

/**
 * Facts about the call's subject, injected into the system prompt (PROJECT.md §4.4,
 * ROADMAP A.3). These are ground truth — the LLM states them but must never alter
 * them. Keyed by the bound scenario's {@code ScenarioDefinition.factSchema()} names
 * (e.g. {@code clientName}, {@code debtAmount}), so the shape varies per scenario.
 * Populated from campaign target {@code context_data} (Stage 10) or {@link
 * TestContextProperties} for manual/test calls.
 *
 * @param facts fact name -> value ({@link String}, {@link java.math.BigDecimal}, or
 *              {@link java.time.LocalDate}, matching the fact's declared type)
 * @param goal  campaign goal / extra instruction appended to the prompt (nullable) —
 *              deliberately not a fact: it is campaign-level, not part of any
 *              scenario's factSchema
 */
public record CallContext(Map<String, Object> facts, String goal) {

    public CallContext {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    /** Convenience lookup; {@code null} if this scenario has no such fact or it was never set. */
    public Object fact(String name) {
        return facts.get(name);
    }
}
