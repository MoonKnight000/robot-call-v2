package uz.murodjon.uysotvoice.aimodel.dto;

/**
 * A company's {@link AiModelConfig} merged over the process-wide defaults ({@code
 * DialogProperties}, {@code spring.ai.google.genai.chat.options.*}) — what {@code
 * DialogEngine} actually applies for one call. {@code model}/{@code temperature}/{@code
 * maxOutputTokens} stay nullable: null means "let Spring AI's auto-configured default
 * through untouched" rather than forcing a value. {@code maxCallSeconds}/{@code
 * maxTokensPerCall} are always resolved (the company row's value, or else {@code
 * DialogProperties}'s), since those two are enforced directly in Java, not merged by
 * Spring AI.
 */
public record EffectiveAiModelConfig(
        String model,
        Double temperature,
        Integer maxOutputTokens,
        int maxCallSeconds,
        long maxTokensPerCall
) {
}
