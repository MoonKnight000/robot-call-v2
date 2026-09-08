package uz.murodjon.robotcallv2.aimodel.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;

import java.util.List;

public interface AiModelRepository {

    /** Every row of {@code kind}; {@code mode} narrows to one pipeline, null takes both. */
    List<AiModel> findByKindAndMode(AiModelKind kind, PipelineMode mode);

    AiModel findById(String id);
}
