package uz.murodjon.uysotvoice.engine.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.engine.domain.EngineConfig;
import uz.murodjon.uysotvoice.engine.entity.EngineConfigEntity;

import java.time.Instant;

/**
 * JPA-backed DAO for {@code engine_config} (§11 settings) — one row per company. Every
 * method takes its {@code companyId} explicitly; resolving "which company am I acting
 * for" is the service's job, not this class's.
 */
@Repository
public class EngineConfigRepository {

    private final EngineConfigJpaRepository jpaRepository;

    public EngineConfigRepository(EngineConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** {@code null} if the company has never chosen an engine — every field falls back to the process default. */
    public EngineConfig findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId)
                .map(EngineConfigRepository::toEngineConfig)
                .orElse(null);
    }

    /**
     * Creates {@code companyId}'s row on first {@code PUT}, updates it after. {@code
     * config}'s {@code companyId}/{@code createdAt} are ignored — the row belongs to the
     * {@code companyId} argument and keeps its original creation time — so callers may
     * pass {@link EngineConfig#overrides}. The saved row is returned with both filled.
     */
    public EngineConfig upsert(long companyId, EngineConfig config) {
        EngineConfigEntity entity = jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            EngineConfigEntity fresh = new EngineConfigEntity();
            fresh.setCompanyId(companyId);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setMode(config.mode());
        entity.setSttProvider(config.sttProvider());
        entity.setTtsProvider(config.ttsProvider());
        entity.setRealtimeProvider(config.realtimeProvider());
        return toEngineConfig(jpaRepository.save(entity));
    }

    private static EngineConfig toEngineConfig(EngineConfigEntity e) {
        return new EngineConfig(e.getCompanyId(), e.getMode(), e.getSttProvider(), e.getTtsProvider(),
                e.getRealtimeProvider(), e.getCreatedAt());
    }
}
