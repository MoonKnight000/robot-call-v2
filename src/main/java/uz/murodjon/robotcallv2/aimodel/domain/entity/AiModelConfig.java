package uz.murodjon.robotcallv2.aimodel.domain.entity;

import java.time.Instant;

/**
 * A company's AI model overrides — the domain model of ai_model_config.
 */
public record AiModelConfig(
        long companyId,
        String model,
        Double temperature,
        Integer maxOutputTokens,
        Integer maxCallSeconds,
        Long maxTokensPerCall,
        Instant createdAt
) {
    public static AiModelConfig overrides(String model, Double temperature, Integer maxOutputTokens,
                                          Integer maxCallSeconds, Long maxTokensPerCall) {
        return new AiModelConfig(0L, model, temperature, maxOutputTokens, maxCallSeconds, maxTokensPerCall, null);
    }
}
