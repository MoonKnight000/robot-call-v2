package uz.murodjon.robotcallv2.shared.dialog;

import java.util.Locale;

/**
 * How much a text LLM may think before it answers — one vendor-neutral setting on the agent.
 * Each vendor gets its own form of it ({@code agent/provider} strategies): Gemini's
 * {@code thinkingLevel}, OpenAI's {@code reasoning_effort}, Claude's thinking budget, each
 * moved to the nearest level the chosen model accepts.
 *
 * <p>More thinking is a slower first word: on a phone call {@link #LOW} or lighter is the
 * normal choice, {@link #MEDIUM}/{@link #HIGH} only for a chat or a call where accuracy beats speed.
 */
public enum ReasoningEffort {

    /** No thinking where the model allows it; the lightest level it accepts otherwise. */
    NONE,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH;

    /**
     * @return the level with this name in any case ({@code "minimal"}, {@code "LOW"}), or
     *         {@code null} for a blank or unknown value — Gemini's {@code THINKING_LEVEL_UNSPECIFIED} included
     */
    public static ReasoningEffort parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String name = value.trim().toUpperCase(Locale.ROOT);
        for (ReasoningEffort effort : values()) {
            if (effort.name().equals(name)) {
                return effort;
            }
        }
        return null;
    }
}
