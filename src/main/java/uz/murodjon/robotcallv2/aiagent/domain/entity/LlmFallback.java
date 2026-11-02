package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.LlmFallbackMode;

import java.util.Objects;

/**
 * The backup LLM of an agent: the model a turn runs on when the main one fails or is too slow.
 * An agent without one has {@code null}, never a half-filled instance.
 *
 * @param model a cascade LLM id from the catalog
 * @param mode  when it takes over; {@link LlmFallbackMode#SEQUENTIAL} unless said otherwise
 */
public record LlmFallback(String model, LlmFallbackMode mode) {

    public LlmFallback {
        Objects.requireNonNull(model, "Fallback model cannot be null");
        if (mode == null) {
            mode = LlmFallbackMode.SEQUENTIAL;
        }
    }
}
