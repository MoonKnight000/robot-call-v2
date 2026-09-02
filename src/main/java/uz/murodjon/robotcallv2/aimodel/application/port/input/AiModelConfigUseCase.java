package uz.murodjon.robotcallv2.aimodel.application.port.input;

import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;

public interface AiModelConfigUseCase {

    AiModelConfig findForCurrentCompany();

    AiModelConfig updateForCurrentCompany(UpdateAiModelConfigRequest request);

    EffectiveAiModelConfig findEffectiveByCompanyId(long companyId);
}
