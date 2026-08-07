package uz.murodjon.uysotvoice.aimodel.domain;

import java.time.Instant;

/**
 * A company's AI model overrides — the domain model of {@code ai_model_config}, passed
 * between service and repository and returned by {@code GET/PUT /api/settings/ai-model}
 * (§11). Every override field is nullable: null means "use the process default", not
 * zero/blank.
 *
 * <p>{@code companyId} and {@code createdAt} are repository-owned: they identify and
 * date the stored row. On the way in ({@link
 * uz.murodjon.uysotvoice.aimodel.repository.AiModelConfigRepository#upsert}) they are
 * ignored and re-stamped; on the way out they are always filled. Use {@link #overrides}
 * to build a value that is only meant to be written.
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

    /**
     * The override set alone, without the repository-owned {@code companyId}/{@code
     * createdAt} — what a {@code PUT} carries. The repository stamps both on save.
     */
    public static AiModelConfig overrides(String model, Double temperature, Integer maxOutputTokens,
                                          Integer maxCallSeconds, Long maxTokensPerCall) {
        return new AiModelConfig(0L, model, temperature, maxOutputTokens, maxCallSeconds, maxTokensPerCall, null);
    }
}
