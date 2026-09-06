package uz.murodjon.robotcallv2.shared.dialog;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * Defines whether the AI agent behaves as an upfront AI assistant or acts human-like.
 */
public enum AgentPersona {
    /**
     * Identifies as an AI robot for the company, speaks the official disclosure,
     * and when unable to answer or confronted with difficult questions, honestly discloses it is an AI.
     */
    AI_ASSISTANT,

    /**
     * Behaves like a real human employee, introduces itself by the voice persona name
     * (e.g. "Salom, mening ismim Dilnavoz..."), never admits being a bot, and strives
     * to answer any question directly and naturally.
     */
    HUMAN_LIKE;

    @JsonCreator
    public static AgentPersona fromString(String value) {
        if (value == null || value.isBlank()) {
            return AI_ASSISTANT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.contains("HUMAN") || normalized.contains("ODAM") || normalized.contains("PERSON")) {
            return HUMAN_LIKE;
        }
        if (normalized.contains("AI") || normalized.contains("ROBOT") || normalized.contains("ASSISTANT") || normalized.contains("BOT")) {
            return AI_ASSISTANT;
        }
        for (AgentPersona persona : values()) {
            if (persona.name().equals(normalized)) {
                return persona;
            }
        }
        return AI_ASSISTANT;
    }
}
