package uz.murodjon.robotcallv2.engine.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.engine.application.mapper.EngineConfigMapper;
import uz.murodjon.robotcallv2.engine.application.port.output.EngineConfigRepository;
import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;
import uz.murodjon.robotcallv2.engine.infrastructure.persistence.entity.EngineConfigEntity;
import uz.murodjon.robotcallv2.engine.infrastructure.persistence.repository.EngineConfigJpaRepository;

import java.time.Instant;

@Component
public class EngineConfigRepositoryAdapter implements EngineConfigRepository {

    private final EngineConfigJpaRepository jpaRepository;
    private final EngineConfigMapper mapper;

    public EngineConfigRepositoryAdapter(EngineConfigJpaRepository jpaRepository, EngineConfigMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public EngineConfig findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId)
                .map(mapper::entityToDomain)
                .orElse(null);
    }

    @Override
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
        return mapper.entityToDomain(jpaRepository.save(entity));
    }
}
