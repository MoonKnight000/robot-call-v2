package uz.murodjon.robotcallv2.engine.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;
import uz.murodjon.robotcallv2.engine.infrastructure.persistence.entity.EngineConfigEntity;

import java.time.Instant;

@Component
public class EngineConfigMapper {

    public EngineConfig entityToDomain(EngineConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new EngineConfig(
                entity.getCompanyId(),
                entity.getMode(),
                entity.getSttProvider(),
                entity.getTtsProvider(),
                entity.getRealtimeProvider(),
                entity.getPipecatStt(),
                entity.getPipecatLlm(),
                entity.getPipecatTts(),
                entity.getCreatedAt()
        );
    }

    public EngineConfigEntity domainToEntity(EngineConfig domain, long companyId) {
        if (domain == null) {
            return null;
        }
        EngineConfigEntity entity = new EngineConfigEntity();
        entity.setCompanyId(companyId);
        entity.setMode(domain.mode());
        entity.setSttProvider(domain.sttProvider());
        entity.setTtsProvider(domain.ttsProvider());
        entity.setRealtimeProvider(domain.realtimeProvider());
        entity.setPipecatStt(domain.pipecatStt());
        entity.setPipecatLlm(domain.pipecatLlm());
        entity.setPipecatTts(domain.pipecatTts());
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : Instant.now());
        return entity;
    }
}
