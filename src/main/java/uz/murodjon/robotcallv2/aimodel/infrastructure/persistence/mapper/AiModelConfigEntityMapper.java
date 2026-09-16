package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelConfigEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Component
public class AiModelConfigEntityMapper {

    public AiModelConfig toAiModelConfig(AiModelConfigEntity entity) {
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

    public AiModelConfigEntity toAiModelConfigEntity(AiModelConfig domain, CompanyEntity company) {
        if (domain == null) {
            return null;
        }
        AiModelConfigEntity entity = new AiModelConfigEntity();
        entity.setCompany(company);
        entity.setModel(domain.model());
        entity.setTemperature(domain.temperature());
        entity.setMaxOutputTokens(domain.maxOutputTokens());
        entity.setMaxCallSeconds(domain.maxCallSeconds());
        entity.setMaxTokensPerCall(domain.maxTokensPerCall());
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : Instant.now());
        return entity;
    }
}
