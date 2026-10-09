package uz.murodjon.robotcallv2.aimodel.domain.entity;

import java.time.Instant;

/**
 * A company's AI model overrides — the domain model of ai_model_config.
 *
 * @param ownLlm the company's own LLM, or null while it runs on the platform's
 */
public record AiModelConfig(
        long companyId,
        String model,
        Double temperature,
        Integer maxOutputTokens,
        Integer maxCallSeconds,
        Long maxTokensPerCall,
        Instant createdAt,
        OwnLlmEndpoint ownLlm
) {
    /** The pre-V11 shape: no own LLM. */
    public AiModelConfig(long companyId, String model, Double temperature, Integer maxOutputTokens,
                         Integer maxCallSeconds, Long maxTokensPerCall, Instant createdAt) {
        this(companyId, model, temperature, maxOutputTokens, maxCallSeconds, maxTokensPerCall, createdAt, null);
    }

    public static AiModelConfig overrides(String model, Double temperature, Integer maxOutputTokens,
                                          Integer maxCallSeconds, Long maxTokensPerCall) {
        return new AiModelConfig(0L, model, temperature, maxOutputTokens, maxCallSeconds, maxTokensPerCall, null);
    }
}
