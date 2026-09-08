package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;

import java.util.LinkedHashMap;
import java.util.List;
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
 * rather than at each prompt factory means a new consumer cannot forget. The memory
 * gets the same treatment: its summaries were written by an LLM from what the client
 * said, and its notes by an operator.
 *
 * @param facts  fact name -> value ({@link String}, {@link java.math.BigDecimal}, or
 *               {@link java.time.LocalDate}, matching the fact's declared type)
 * @param goal   campaign goal / extra instruction appended to the prompt (nullable) —
 *               deliberately not a fact: it is campaign-level, not part of any
 *               scenario's factSchema
 * @param memory what the company remembers about this phone from earlier calls
 *               (nullable — a first conversation has none)
 */
public record CallContext(Map<String, Object> facts, String goal, ClientMemory memory) {

    /** A fact fills one line of the prompt: a name, a sum, a date, a contract number. */
    private static final int MAX_FACT_CHARS = 200;

    /** The goal is a sentence or two of campaign instruction, so it gets more room. */
    private static final int MAX_GOAL_CHARS = 500;

    /** A remembered call is the summary LLM's two or three sentences. */
    private static final int MAX_SUMMARY_CHARS = 400;

    public CallContext {
        facts = sanitize(facts);
        goal = PromptSafeText.sanitize(goal, MAX_GOAL_CHARS);
        memory = sanitize(memory);
    }

    public CallContext(Map<String, Object> facts, String goal) {
        this(facts, goal, null);
    }

    public CallContext withMemory(ClientMemory memory) {
        return new CallContext(facts, goal, memory);
    }

    public CallContext withMergedFacts(Map<String, Object> additionalFacts) {
        if (additionalFacts == null || additionalFacts.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(facts);
        merged.putAll(additionalFacts);
        return new CallContext(merged, goal, memory);
    }

    public String clientPhone() {
        Object p = fact("phone");
        if (p == null) {
            p = fact("clientPhone");
        }
        return p != null ? String.valueOf(p) : null;
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

    private static ClientMemory sanitize(ClientMemory memory) {
        if (memory == null) {
            return null;
        }
        List<RememberedCall> calls = memory.recentCalls().stream()
                .map(call -> new RememberedCall(call.at(), call.scenarioKey(), call.disposition(),
                        PromptSafeText.sanitize(call.summary(), MAX_SUMMARY_CHARS)))
                .toList();
        return new ClientMemory(memory.id(), memory.companyId(), memory.phone(),
                PromptSafeText.sanitize(memory.preferredName(), MAX_FACT_CHARS),
                memory.preferredLanguage(),
                PromptSafeText.sanitize(memory.operatorNotes(), MAX_GOAL_CHARS),
                calls, sanitize(memory.facts()), memory.updatedAt());
    }

    /** Convenience lookup; {@code null} if this scenario has no such fact or it was never set. */
    public Object fact(String name) {
        return facts.get(name);
    }
}
