package uz.murodjon.robotcallv2.aimodel.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aimodel.application.dto.AiModelResponse;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;

@Component
public class AiModelMapper {

    public AiModelResponse toAiModelResponse(AiModel model, long pricePerMinuteUzs) {
        return new AiModelResponse(model.id(), model.kind(), model.provider(), model.mode(), model.label(),
                model.tier(), model.suggested(), pricePerMinuteUzs);
    }
}
