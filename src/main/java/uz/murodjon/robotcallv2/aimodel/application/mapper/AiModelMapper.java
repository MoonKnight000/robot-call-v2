package uz.murodjon.robotcallv2.aimodel.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelEntity;

@Component
public class AiModelMapper {

    public AiModel toAiModel(AiModelEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AiModel(entity.getId(), entity.getKind(), entity.getProvider(), entity.getMode(), entity.getLabel());
    }
}
