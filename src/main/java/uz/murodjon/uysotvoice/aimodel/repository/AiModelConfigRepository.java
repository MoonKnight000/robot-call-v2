package uz.murodjon.uysotvoice.aimodel.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.aimodel.dto.AiModelConfig;
import uz.murodjon.uysotvoice.aimodel.entity.AiModelConfigEntity;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;

import java.time.Instant;

/** JPA-backed DAO for {@code ai_model_config} (§11 settings) — one row per company. */
@Repository
public class AiModelConfigRepository {

    private final AiModelConfigJpaRepository jpa;
    private final CurrentCompany company;

    public AiModelConfigRepository(AiModelConfigJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    /** {@code null} if the current company has never set an override — every field falls back to the process default. */
    public AiModelConfig find() {
        return jpa.findByCompanyId(company.id()).map(AiModelConfigRepository::toRow).orElse(null);
    }

    /** Also used by {@code DialogEngine} to resolve a specific company's overrides at call time. */
    public AiModelConfig find(long companyId) {
        return jpa.findByCompanyId(companyId).map(AiModelConfigRepository::toRow).orElse(null);
    }

    /** Upsert: the current company's row is created on first {@code PUT}, updated after. */
    public AiModelConfig save(String model, Double temperature, Integer maxOutputTokens,
                              Integer maxCallSeconds, Long maxTokensPerCall) {
        long companyId = company.id();
        AiModelConfigEntity entity = jpa.findByCompanyId(companyId).orElseGet(() -> {
            AiModelConfigEntity fresh = new AiModelConfigEntity();
            fresh.setCompanyId(companyId);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setModel(model);
        entity.setTemperature(temperature);
        entity.setMaxOutputTokens(maxOutputTokens);
        entity.setMaxCallSeconds(maxCallSeconds);
        entity.setMaxTokensPerCall(maxTokensPerCall);
        return toRow(jpa.save(entity));
    }

    private static AiModelConfig toRow(AiModelConfigEntity e) {
        return new AiModelConfig(e.getCompanyId(), e.getModel(), e.getTemperature(), e.getMaxOutputTokens(),
                e.getMaxCallSeconds(), e.getMaxTokensPerCall(), e.getCreatedAt());
    }
}
