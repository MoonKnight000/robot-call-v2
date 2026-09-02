package uz.murodjon.robotcallv2.aimodel.domain.entity;

/**
 * Merged runtime AI model configuration for a company.
 */
public record EffectiveAiModelConfig(
        String model,
        Double temperature,
        Integer maxOutputTokens,
        int maxCallSeconds,
        long maxTokensPerCall
) {
}
