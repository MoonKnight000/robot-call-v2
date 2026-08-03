package uz.murodjon.uysotvoice.aimodel.dto;

import java.time.Instant;

/**
 * {@code GET/PUT /api/settings/ai-model} (§11) — a company's overrides. Every field is
 * nullable: null means "use the process default", not zero/blank.
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
}
