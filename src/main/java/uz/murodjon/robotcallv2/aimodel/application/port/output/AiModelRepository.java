package uz.murodjon.robotcallv2.aimodel.application.port.output;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

import java.util.List;

public interface AiModelRepository {

    List<AiModel> findByMode(PipelineMode mode);

    AiModel findById(String id);
}
