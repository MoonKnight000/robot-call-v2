package uz.murodjon.uysotvoice.aimodel.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.aimodel.domain.AiModelConfig;
import uz.murodjon.uysotvoice.aimodel.entity.AiModelConfigEntity;

import java.time.Instant;

/**
 * JPA-backed DAO for {@code ai_model_config} (§11 settings) — one row per company.
 * Every method takes its {@code companyId} explicitly; resolving "which company am I
 * acting for" is the service's job, not this class's.
 */
@Repository
public class AiModelConfigRepository {

    private final AiModelConfigJpaRepository jpaRepository;

    public AiModelConfigRepository(AiModelConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** {@code null} if the company has never set an override — every field falls back to the process default. */
    public AiModelConfig findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId)
                .map(AiModelConfigRepository::toAiModelConfig)
                .orElse(null);
    }

    /**
     * Creates {@code companyId}'s row on first {@code PUT}, updates it after. {@code
     * config}'s {@code companyId}/{@code createdAt} are ignored — the row belongs to the
     * {@code companyId} argument and keeps its original creation time — so callers may
     * pass {@link AiModelConfig#overrides}. The saved row is returned with both filled.
     */
    public AiModelConfig upsert(long companyId, AiModelConfig config) {
        AiModelConfigEntity entity = jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            AiModelConfigEntity fresh = new AiModelConfigEntity();
            fresh.setCompanyId(companyId);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setModel(config.model());
        entity.setTemperature(config.temperature());
        entity.setMaxOutputTokens(config.maxOutputTokens());
        entity.setMaxCallSeconds(config.maxCallSeconds());
        entity.setMaxTokensPerCall(config.maxTokensPerCall());
        return toAiModelConfig(jpaRepository.save(entity));
    }

    private static AiModelConfig toAiModelConfig(AiModelConfigEntity e) {
        return new AiModelConfig(e.getCompanyId(), e.getModel(), e.getTemperature(), e.getMaxOutputTokens(),
                e.getMaxCallSeconds(), e.getMaxTokensPerCall(), e.getCreatedAt());
    }
}
