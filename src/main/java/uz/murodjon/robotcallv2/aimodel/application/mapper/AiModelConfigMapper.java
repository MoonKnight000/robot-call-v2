package uz.murodjon.robotcallv2.aimodel.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelConfigEntity;

import java.time.Instant;

@Component
public class AiModelConfigMapper {

    public AiModelConfig entityToDomain(AiModelConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AiModelConfig(
                entity.getCompanyId(),
                entity.getModel(),
                entity.getTemperature(),
                entity.getMaxOutputTokens(),
                entity.getMaxCallSeconds(),
                entity.getMaxTokensPerCall(),
                entity.getCreatedAt()
        );
    }

    public AiModelConfigEntity domainToEntity(AiModelConfig domain, long companyId) {
        if (domain == null) {
            return null;
        }
        AiModelConfigEntity entity = new AiModelConfigEntity();
        entity.setCompanyId(companyId);
        entity.setModel(domain.model());
        entity.setTemperature(domain.temperature());
        entity.setMaxOutputTokens(domain.maxOutputTokens());
        entity.setMaxCallSeconds(domain.maxCallSeconds());
        entity.setMaxTokensPerCall(domain.maxTokensPerCall());
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : Instant.now());
        return entity;
    }
}
